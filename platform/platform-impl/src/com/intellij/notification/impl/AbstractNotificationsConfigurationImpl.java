// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

/**
 * @author brian.mcnamara
 **/
abstract class AbstractNotificationsConfigurationImpl extends NotificationsConfiguration implements PersistentStateComponent<Element> {

  private static final Logger LOG = Logger.getInstance(NotificationsConfiguration.class);

  static final String SHOW_BALLOONS_ATTRIBUTE = "showBalloons";
  static final String SYSTEM_NOTIFICATIONS_ATTRIBUTE = "systemNotifications";
  static final Comparator<NotificationSettings> NOTIFICATION_SETTINGS_COMPARATOR =
    (o1, o2) -> o1.getGroupId().compareToIgnoreCase(o2.getGroupId());

  final Map<String, String> myToolWindowCapable = new THashMap<>();
  final Map<String, NotificationSettings> myIdToSettingsMap = new THashMap<>();


  public boolean SHOW_BALLOONS = true;
  public boolean SYSTEM_NOTIFICATIONS = true;

  @Override
  public synchronized Element getState() {
    Element element = new Element("NotificationsConfiguration");

    NotificationSettings[] sortedNotifications = myIdToSettingsMap.values().toArray(new NotificationSettings[0]);
    Arrays.sort(sortedNotifications, NOTIFICATION_SETTINGS_COMPARATOR);
    for (NotificationSettings settings : sortedNotifications) {
      element.addContent(settings.save());
    }

    //noinspection NonPrivateFieldAccessedInSynchronizedContext
    if (!SHOW_BALLOONS) {
      element.setAttribute(SHOW_BALLOONS_ATTRIBUTE, "false");
    }

    //noinspection NonPrivateFieldAccessedInSynchronizedContext
    if (!SYSTEM_NOTIFICATIONS) {
      element.setAttribute(SYSTEM_NOTIFICATIONS_ATTRIBUTE, "false");
    }

    return element;
  }

  @Override
  public synchronized void loadState(@NotNull final Element state) {
    myIdToSettingsMap.clear();
    for (Element child : state.getChildren("notification")) {
      final NotificationSettings settings = NotificationSettings.load(child);
      if (settings != null) {
        final String id = settings.getGroupId();
        LOG.assertTrue(!myIdToSettingsMap.containsKey(id), String.format("Settings for '%s' already loaded!", id));
        myIdToSettingsMap.put(id, settings);
      }
    }
    doRemove("Log Only");

    if ("false".equals(state.getAttributeValue(SHOW_BALLOONS_ATTRIBUTE))) {
      //noinspection NonPrivateFieldAccessedInSynchronizedContext
      SHOW_BALLOONS = false;
    }

    if ("false".equals(state.getAttributeValue(SYSTEM_NOTIFICATIONS_ATTRIBUTE))) {
      //noinspection NonPrivateFieldAccessedInSynchronizedContext
      SYSTEM_NOTIFICATIONS = false;
    }
  }


  @Override
  public void changeSettings(String groupDisplayName, NotificationDisplayType displayType, boolean shouldLog, boolean shouldReadAloud) {
    changeSettings(new NotificationSettings(groupDisplayName, displayType, shouldLog, shouldReadAloud));
  }

  public synchronized void changeSettings(NotificationSettings settings) {
    String groupDisplayName = settings.getGroupId();
    if (settings.equals(getDefaultSettings(groupDisplayName))) {
      myIdToSettingsMap.remove(groupDisplayName);
    }
    else {
      myIdToSettingsMap.put(groupDisplayName, settings);
    }
  }

  @NotNull
  static NotificationSettings getDefaultSettings(String groupId) {
    NotificationGroup group = NotificationGroup.findRegisteredGroup(groupId);
    if (group != null) {
      return new NotificationSettings(groupId, group.getDisplayType(), group.isLogByDefault(), false);
    }
    return new NotificationSettings(groupId, NotificationDisplayType.BALLOON, true, false);
  }

  @Nullable
  synchronized NotificationSettings getSettingsNullable(@NotNull String groupId) {
    return myIdToSettingsMap.get(groupId);
  }

  synchronized void doRemove(String... toRemove) {
    for (String groupId : toRemove) {
      myIdToSettingsMap.remove(groupId);
      myToolWindowCapable.remove(groupId);
    }
  }
}
