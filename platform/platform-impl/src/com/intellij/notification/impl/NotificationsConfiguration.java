/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.messages.MessageBus;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author spleaner
 */
@State(name = "NotificationConfiguration",
       storages = {@Storage(id = "other", file = "$APP_CONFIG$/notifications.xml")})
public class NotificationsConfiguration implements ApplicationComponent, Notifications, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.notification.impl.NotificationsConfiguration");

  private final Map<String, NotificationSettings> myIdToSettingsMap = new LinkedHashMap<String, NotificationSettings>();
  private final MessageBus myMessageBus;

  public NotificationsConfiguration(@NotNull final MessageBus bus) {
    myMessageBus = bus;
  }

  public static NotificationsConfiguration getNotificationsConfiguration() {
    return ApplicationManager.getApplication().getComponent(NotificationsConfiguration.class);
  }

  public static NotificationSettings[] getAllSettings() {
    return getNotificationsConfiguration()._getAllSettings();
  }

  @Deprecated
  public static void remove(NotificationSettings[] toRemove) {
    getNotificationsConfiguration()._remove(ContainerUtil.map2Array(toRemove, String.class, new Function<NotificationSettings, String>() {
      @Override
      public String fun(NotificationSettings notificationSettings) {
        return notificationSettings.getGroupId();
      }
    }));
  }

  public static void remove(String... toRemove) {
    getNotificationsConfiguration()._remove(toRemove);
  }

  private synchronized void _remove(String... toRemove) {
    for (final String id : toRemove) {
      myIdToSettingsMap.remove(id);
    }
  }

  private synchronized NotificationSettings[] _getAllSettings() {
    final List<NotificationSettings> result = new ArrayList<NotificationSettings>(myIdToSettingsMap.values());

    Collections.sort(result, new Comparator<NotificationSettings>() {
      public int compare(NotificationSettings o1, NotificationSettings o2) {
        return o1.getGroupId().compareToIgnoreCase(o2.getGroupId());
      }
    });

    return result.toArray(new NotificationSettings[result.size()]);
  }

  @Nullable
  private synchronized NotificationSettings _getSettings(@NotNull final String groupId) {
    return myIdToSettingsMap.get(groupId);
  }

  @NotNull
  public static NotificationSettings getSettings(@NotNull final String groupId) {
    final NotificationSettings settings = getNotificationsConfiguration()._getSettings(groupId);
    return settings == null ? new NotificationSettings(groupId, NotificationDisplayType.STICKY_BALLOON, true) : settings;
  }

  @NotNull
  public String getComponentName() {
    return "NotificationsConfiguration";
  }

  public void initComponent() {
    myMessageBus.connect().subscribe(TOPIC, this);
  }

  public synchronized void disposeComponent() {
    myIdToSettingsMap.clear();
  }

  public void register(@NotNull final String groupDisplayType, @NotNull final NotificationDisplayType displayType) {
    registerDefaultSettings(new NotificationSettings(groupDisplayType, displayType));
  }

  public synchronized void registerDefaultSettings(NotificationSettings settings) {
    if (!isRegistered(settings.getGroupId())) {
      myIdToSettingsMap.put(settings.getGroupId(), settings);
    }
  }

  public synchronized boolean isRegistered(@NotNull final String id) {
    return myIdToSettingsMap.containsKey(id);
  }

  public void notify(@NotNull Notification notification) {
  }

  public synchronized Element getState() {
    @NonNls Element element = new Element("NotificationsConfiguration");
    for (NotificationSettings settings : myIdToSettingsMap.values()) {
      element.addContent(settings.save());
    }

    return element;
  }

  public synchronized void loadState(final Element state) {
    for (@NonNls Element child : (Iterable<? extends Element>)state.getChildren("notification")) {
      final NotificationSettings settings = NotificationSettings.load(child);
      if (settings != null) {
        final String id = settings.getGroupId();
        LOG.assertTrue(!myIdToSettingsMap.containsKey(id), String.format("Settings for '%s' already loaded!", id));
        myIdToSettingsMap.put(id, settings);
      }
    }
    _remove("Log Only");
  }
}
