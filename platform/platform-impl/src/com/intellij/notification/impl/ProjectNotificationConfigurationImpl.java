// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/**
 * Allows project based overrides of notification settings. This class will check settings first in the project level,
 * if the setting does not exist, it will delegate to the Application level configuration.
 * @author brian.mcnamara
 **/
@State(
  name = "NotificationConfiguration",
  storages = @Storage("notifications.xml")
)
public class ProjectNotificationConfigurationImpl extends AbstractNotificationsConfigurationImpl {

  private final NotificationsConfigurationImpl myApplicationNotificationsConfiguration;

  public ProjectNotificationConfigurationImpl(NotificationsConfigurationImpl notificationsConfiguration) {
    this.myApplicationNotificationsConfiguration = notificationsConfiguration;
  }

  @Override
  public synchronized void changeSettings(NotificationSettings settings) {
    if (containsSettings(settings.getGroupId())) {
      super.changeSettings(settings);
    } else {
      myApplicationNotificationsConfiguration.changeSettings(settings);
    }
  }

  public synchronized NotificationSettings getSettings(@NotNull String groupId) {
    NotificationSettings settings = getSettingsNullable(groupId);
    if (settings == null) {
      settings = myApplicationNotificationsConfiguration.getSettingsNullable(groupId);
    }
    return settings == null ? getDefaultSettings(groupId) : settings;
  }

  private boolean containsSettings(@NotNull String groupId) {
    return myIdToSettingsMap.containsKey(groupId);
  }
}
