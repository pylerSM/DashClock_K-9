/*
 * Copyright 2013 Christian Ketterer (cketti)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.cketti.dashclock.k9;

import java.util.List;
import java.util.Set;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Settings;

import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;

import de.cketti.dashclock.k9.K9Helper.Account;


public class K9Extension extends DashClockExtension {
    private static final String PLAY_STORE_URL_PREFIX =
            "https://play.google.com/store/apps/details?id=";

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateDataInBackground(UPDATE_REASON_CONTENT_CHANGED);
        }
    };

    @Override
    protected void onInitialize(boolean isReconnect) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(K9Helper.BroadcastIntents.ACTION_EMAIL_RECEIVED);
        filter.addAction(K9Helper.BroadcastIntents.ACTION_EMAIL_DELETED);
        filter.addAction(K9Helper.BroadcastIntents.ACTION_REFRESH_OBSERVER);

        registerReceiver(mReceiver, filter);
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(mReceiver);
        } catch (Exception e) { /* ignore */ }

        super.onDestroy();
    }

    @Override
    protected void onUpdateData(int reason) {

        if (!isK9AvailableAndSetUp()) {
            return;
        }

        int unreadCount = 0;
        StringBuilder body = new StringBuilder();

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> enabledAccounts = sp.getStringSet("accounts_list", null);

        List<Account> accounts = K9Helper.getAccounts(this);
        if (accounts != null) {
            for (Account account : accounts) {
                if (enabledAccounts != null && !enabledAccounts.contains(account.uuid)) {
                    continue;
                }

                int accountUnread = K9Helper.getUnreadCount(this, account);
                unreadCount += accountUnread;

                if (accountUnread > 0) {
                    if (body.length() > 0) {
                        body.append('\n');
                    }

                    body.append(account.name).append(" (").append(accountUnread).append(')');
                }
            }
        }

        ExtensionData data = new ExtensionData()
                .visible(unreadCount > 0)
                .icon(R.drawable.ic_envelope)
                .status(Integer.toString(unreadCount))
                .expandedTitle(getQuantityString(R.plurals.unread_messages_title, unreadCount))
                .expandedBody(body.toString())
                .clickIntent(K9Helper.getStartK9Intent(this));

        publishUpdate(data);
    }

    private void displayErrorMessage(String errorMessage, Intent intent) {
        ExtensionData data = new ExtensionData()
            .visible(true)
            .icon(R.drawable.ic_envelope)
            .status(getString(R.string.status_error))
            .expandedTitle(getString(R.string.status_error))
            .expandedBody(errorMessage)
            .clickIntent(intent);

        publishUpdate(data);
    }

    private boolean isK9AvailableAndSetUp() {
        boolean installed = K9Helper.isK9Installed(this);
        boolean enabled = K9Helper.isK9Enabled(this);
        boolean hasPermission = K9Helper.hasK9ReadPermission(this);

        if (!installed) {
            displayErrorMessage(getString(R.string.error_k9_not_installed), getPlayStoreIntent());
            return false;
        } else if (!enabled) {
            displayErrorMessage(getString(R.string.error_k9_not_enabled),
                    K9Helper.getStartK9Intent(this));
            return false;
        } else if (!hasPermission) {
            displayErrorMessage(getString(R.string.error_k9_no_permission,
                    getString(R.string.app_name)), getAppInfoIntent());
            return false;
        }

        return true;
    }

    private Intent getPlayStoreIntent() {
        Uri uri = Uri.parse(PLAY_STORE_URL_PREFIX + K9Helper.PACKAGE_NAME);
        return new Intent(Intent.ACTION_VIEW, uri);
    }

    private Intent getAppInfoIntent() {
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
    }
}
