/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.notification.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author spleaner
 */
@State(name = "NotificationConfiguration",
       storages = {@Storage( file = StoragePathMacros.APP_CONFIG + "/notifications.xml")})
public class NotificationsConfigurationImpl extends NotificationsConfiguration implements ExportableApplicationComponent,
                                                                                          PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.notification.impl.NotificationsConfiguration");
  private static final String SHOW_BALLOONS_ATTRIBUTE = "showBalloons";

  private final Map<String, NotificationSettings> myIdToSettingsMap = new LinkedHashMap<String, NotificationSettings>();
  private final Map<String, String> myToolWindowCapable = new LinkedHashMap<String, String>();
  private final MessageBus myMessageBus;
  public boolean SHOW_BALLOONS = true;

  public static NotificationsConfigurationImpl getNotificationsConfigurationImpl() {
    return (NotificationsConfigurationImpl)getNotificationsConfiguration();
  }

  public NotificationsConfigurationImpl(@NotNull final MessageBus bus) {
    myMessageBus = bus;
  }

  @Override
  public synchronized void registerToolWindowCapability(@NotNull String groupId, @NotNull String toolWindowId) {
    myToolWindowCapable.put(groupId, toolWindowId);
  }

  public synchronized boolean hasToolWindowCapability(@NotNull String groupId) {
    return myToolWindowCapable.containsKey(groupId);
  }

  @Nullable
  public synchronized String getToolWindowId(@NotNull String groupId) {
    return myToolWindowCapable.get(groupId);
  }

  public static NotificationSettings[] getAllSettings() {
    return getNotificationsConfigurationImpl()._getAllSettings();
  }

  @Deprecated
  public static void remove(NotificationSettings[] toRemove) {
    getNotificationsConfigurationImpl()._remove(ContainerUtil.map2Array(toRemove, String.class, new Function<NotificationSettings, String>() {
      @Override
      public String fun(NotificationSettings notificationSettings) {
        return notificationSettings.getGroupId();
      }
    }));
  }

  public static void remove(String... toRemove) {
    getNotificationsConfigurationImpl()._remove(toRemove);
  }

  private synchronized void _remove(String... toRemove) {
    for (final String id : toRemove) {
      myIdToSettingsMap.remove(id);
    }
  }

  private synchronized NotificationSettings[] _getAllSettings() {
    Collection<NotificationSettings> settings = myIdToSettingsMap.values();
    NotificationSettings[] result = settings.toArray(new NotificationSettings[settings.size()]);
    Arrays.sort(result, new Comparator<NotificationSettings>() {
      @Override
      public int compare(NotificationSettings o1, NotificationSettings o2) {
        return o1.getGroupId().compareToIgnoreCase(o2.getGroupId());
      }
    });
    return result;
  }

  @Nullable
  private synchronized NotificationSettings _getSettings(@NotNull final String groupId) {
    return myIdToSettingsMap.get(groupId);
  }

  @NotNull
  public static NotificationSettings getSettings(@NotNull final String groupId) {
    final NotificationSettings settings = getNotificationsConfigurationImpl()._getSettings(groupId);
    return settings == null ? new NotificationSettings(groupId, NotificationDisplayType.BALLOON, true, false) : settings;
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "NotificationsConfiguration";
  }

  @Override
  public void initComponent() {
    myMessageBus.connect().subscribe(TOPIC, this);
  }

  @Override
  public synchronized void disposeComponent() {
    myIdToSettingsMap.clear();
  }

  @Override
  public void register(@NotNull final String groupDisplayName, @NotNull final NotificationDisplayType displayType) {
    register(groupDisplayName, displayType, true);
  }

  @Override
  public void register(@NotNull String groupDisplayName,
                       @NotNull NotificationDisplayType displayType,
                       boolean shouldLog) {
    register(groupDisplayName, displayType, shouldLog, false);
  }

  @Override
  public void register(@NotNull String groupDisplayName,
                       @NotNull NotificationDisplayType displayType,
                       boolean shouldLog,
                       boolean shouldReadAloud) {
    if (!isRegistered(groupDisplayName)) {
      changeSettings(groupDisplayName, displayType, shouldLog, shouldReadAloud);
    } else if (displayType == NotificationDisplayType.TOOL_WINDOW && !hasToolWindowCapability(groupDisplayName)) {
      // the first time with tool window capability
      ObjectUtils.assertNotNull(_getSettings(groupDisplayName)).setDisplayType(NotificationDisplayType.TOOL_WINDOW);
      myToolWindowCapable.put(groupDisplayName, null);
    }

  }

  @Override
  public void changeSettings(String groupDisplayName, NotificationDisplayType displayType, boolean shouldLog, boolean shouldReadAloud) {
    myIdToSettingsMap.put(groupDisplayName, new NotificationSettings(groupDisplayName, displayType, shouldLog, shouldReadAloud));
  }

  public synchronized boolean isRegistered(@NotNull final String id) {
    return myIdToSettingsMap.containsKey(id);
  }

  @Override
  public synchronized Element getState() {
    @NonNls Element element = new Element("NotificationsConfiguration");
    for (NotificationSettings settings : myIdToSettingsMap.values()) {
      element.addContent(settings.save());
    }
    for (String entry: myToolWindowCapable.keySet()) {
      element.addContent(new Element("toolWindow").setAttribute("group", entry));
    }
    //noinspection NonPrivateFieldAccessedInSynchronizedContext
    if (!SHOW_BALLOONS) {
      element.setAttribute(SHOW_BALLOONS_ATTRIBUTE, "false");
    }

    return element;
  }

  @Override
  public synchronized void loadState(final Element state) {
    myIdToSettingsMap.clear();
    for (@NonNls Element child : state.getChildren("notification")) {
      final NotificationSettings settings = NotificationSettings.load(child);
      if (settings != null) {
        final String id = settings.getGroupId();
        LOG.assertTrue(!myIdToSettingsMap.containsKey(id), String.format("Settings for '%s' already loaded!", id));
        myIdToSettingsMap.put(id, settings);
      }
    }
    for (@NonNls Element child : state.getChildren("toolWindow")) {
      String group = child.getAttributeValue("group");
      if (group != null && !myToolWindowCapable.containsKey(group)) {
        myToolWindowCapable.put(group, null);
      }
    }
    _remove("Log Only");
    if ("false".equals(state.getAttributeValue(SHOW_BALLOONS_ATTRIBUTE))) {
      //noinspection NonPrivateFieldAccessedInSynchronizedContext
      SHOW_BALLOONS = false;
    }
  }

  @NotNull
  @Override
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("notifications")};
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Notifications";
  }
}
