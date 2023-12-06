// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl;

import com.intellij.notification.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(name = "NotificationConfiguration", storages = @Storage("notifications.xml"), category = SettingsCategory.UI)
public final class NotificationsConfigurationImpl extends NotificationsConfiguration implements PersistentStateComponent<Element>, Disposable {
  private static final Logger LOG = Logger.getInstance(NotificationsConfigurationImpl.class);
  private static final String SHOW_BALLOONS_ATTRIBUTE = "showBalloons";
  private static final String SYSTEM_NOTIFICATIONS_ATTRIBUTE = "systemNotifications";
  private static final String NOTIFICATION_ANNOUNCING_MODE_ATTRIBUTE = "notificationsAnnouncingMode";

  private static final Comparator<NotificationSettings> NOTIFICATION_SETTINGS_COMPARATOR =
    (o1, o2) -> o1.getGroupId().compareToIgnoreCase(o2.getGroupId());

  private final Map<String, NotificationSettings> myIdToSettingsMap = new HashMap<>();
  private final Map<String, String> myToolWindowCapable = new HashMap<>();

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  public boolean SHOW_BALLOONS = true;
  public boolean SYSTEM_NOTIFICATIONS = true;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private NotificationAnnouncingMode NOTIFICATION_ANNOUNCING_MODE;

  public static NotificationsConfigurationImpl getInstanceImpl() {
    return (NotificationsConfigurationImpl)getNotificationsConfiguration();
  }

  public synchronized boolean hasToolWindowCapability(@NotNull String groupId) {
    return getToolWindowId(groupId) != null || myToolWindowCapable.containsKey(groupId);
  }

  static final class MyNotificationListener implements Notifications {
    @Override
    public void notify(@NotNull Notification notification) {
      getInstanceImpl().notify(notification);
    }
  }

  public @Nullable String getToolWindowId(@NotNull String groupId) {
    NotificationGroup group = NotificationGroup.findRegisteredGroup(groupId);
    return group == null ? null : group.getToolWindowId();
  }

  public synchronized NotificationSettings[] getAllSettings() {
    Collection<NotificationSettings> settings = new HashSet<>(myIdToSettingsMap.values());
    for (NotificationGroup group : NotificationGroup.Companion.getAllRegisteredGroups()) {
      if (group.getDisplayId().startsWith(LIGHTWEIGHT_PREFIX) || group.isHideFromSettings()) {
        continue;
      }
      settings.add(getSettings(group.getDisplayId()));
    }
    NotificationSettings[] result = settings.toArray(new NotificationSettings[0]);
    Arrays.sort(result, NOTIFICATION_SETTINGS_COMPARATOR);
    return result;
  }

  public static void remove(String... toRemove) {
    getInstanceImpl().doRemove(toRemove);
  }

  private synchronized void doRemove(String... toRemove) {
    for (String groupId : toRemove) {
      myIdToSettingsMap.remove(groupId);
      myToolWindowCapable.remove(groupId);
    }
  }

  @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
  public static @NotNull NotificationSettings getSettings(@NotNull String groupId) {
    NotificationSettings settings;
    NotificationsConfigurationImpl impl = getInstanceImpl();
    synchronized (impl) {
      settings = impl.myIdToSettingsMap.get(groupId);
    }
    return settings == null ? getDefaultSettings(groupId) : settings;
  }

  private static @NotNull NotificationSettings getDefaultSettings(String groupId) {
    NotificationGroup group = NotificationGroup.findRegisteredGroup(groupId);
    if (group != null) {
      return new NotificationSettings(groupId, group.getDisplayType(), group.isLogByDefault(), false);
    }
    return new NotificationSettings(groupId, NotificationDisplayType.BALLOON, true, false);
  }

  @Override
  public synchronized void dispose() {
    myIdToSettingsMap.clear();
  }

  @Override
  public void register(@NotNull String groupDisplayName, @NotNull NotificationDisplayType displayType) {
    register(groupDisplayName, displayType, true);
  }

  @Override
  public void register(@NotNull String groupDisplayName, @NotNull NotificationDisplayType displayType, boolean shouldLog) {
    register(groupDisplayName, displayType, shouldLog, false);
  }

  @Override
  public void register(@NotNull String groupDisplayName,
                       @NotNull NotificationDisplayType displayType,
                       boolean shouldLog,
                       boolean shouldReadAloud) {
    register(groupDisplayName, displayType, shouldLog, shouldReadAloud, null);
  }

  @Override
  public void register(@NotNull String groupDisplayName,
                       @NotNull NotificationDisplayType displayType,
                       boolean shouldLog,
                       boolean shouldReadAloud,
                       @Nullable String toolWindowId) {
    if (!isRegistered(groupDisplayName)) {
      // register a new group and remember these settings as default
      new NotificationGroup(groupDisplayName, displayType, shouldLog, toolWindowId);
      // and decide whether to save them explicitly (in case of non-default shouldReadAloud)
      changeSettings(groupDisplayName, displayType, shouldLog, shouldReadAloud);
    }
    else if (displayType == NotificationDisplayType.TOOL_WINDOW && !hasToolWindowCapability(groupDisplayName)) {
      // the first time with tool window capability
      changeSettings(getSettings(groupDisplayName).withDisplayType(NotificationDisplayType.TOOL_WINDOW));
      myToolWindowCapable.put(groupDisplayName, null);
    }
  }

  @Override
  public boolean areNotificationsEnabled() {
    return SHOW_BALLOONS;
  }

  @Override
  public @NotNull NotificationAnnouncingMode getNotificationAnnouncingMode() {
    if (NOTIFICATION_ANNOUNCING_MODE != null) return NOTIFICATION_ANNOUNCING_MODE;
    else if (SystemInfo.isWindows) return NotificationAnnouncingMode.NONE;
    else return NotificationAnnouncingMode.MEDIUM;
  }

  @Override
  public void setNotificationAnnouncingMode(@NotNull NotificationAnnouncingMode mode) {
    NOTIFICATION_ANNOUNCING_MODE = mode;
  }

  @Override
  public @NotNull NotificationDisplayType getDisplayType(@NotNull String groupId) {
    return getSettings(groupId).getDisplayType();
  }

  @Override
  public void setDisplayType(@NotNull String groupId, @NotNull NotificationDisplayType displayType) {
    changeSettings(getSettings(groupId).withDisplayType(displayType));
  }

  @Override
  public void changeSettings(@NotNull String groupId, @NotNull NotificationDisplayType displayType, boolean shouldLog, boolean shouldReadAloud) {
    changeSettings(new NotificationSettings(groupId, displayType, shouldLog, shouldReadAloud));
  }

  public synchronized void changeSettings(@NotNull NotificationSettings settings) {
    String groupDisplayName = settings.getGroupId();
    if (settings.equals(getDefaultSettings(groupDisplayName))) {
      myIdToSettingsMap.remove(groupDisplayName);
    }
    else {
      myIdToSettingsMap.put(groupDisplayName, settings);
    }
  }

  public synchronized boolean isRegistered(@NotNull String id) {
    return myIdToSettingsMap.containsKey(id) || NotificationGroup.isGroupRegistered(id);
  }

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

    if (NOTIFICATION_ANNOUNCING_MODE != null) {
      element.setAttribute(NOTIFICATION_ANNOUNCING_MODE_ATTRIBUTE, NOTIFICATION_ANNOUNCING_MODE.getStringValue());
    }

    return element;
  }

  @Override
  public synchronized void loadState(@NotNull Element state) {
    myIdToSettingsMap.clear();
    for (Element child : state.getChildren("notification")) {
      NotificationSettings settings = NotificationSettings.Companion.load(child);
      if (settings != null) {
        String id = settings.getGroupId();
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

    NotificationAnnouncingMode announcingMode = NotificationAnnouncingMode.get(state.getAttributeValue(NOTIFICATION_ANNOUNCING_MODE_ATTRIBUTE));
    if (announcingMode != null) {
      NOTIFICATION_ANNOUNCING_MODE = announcingMode;
    }
  }
}
