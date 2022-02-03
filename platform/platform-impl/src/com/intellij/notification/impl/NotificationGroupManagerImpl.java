// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NotificationGroupManagerImpl implements NotificationGroupManager {
  private static final Logger LOG = Logger.getInstance(NotificationGroupManagerImpl.class);
  private final Map<String, NotificationGroup> myRegisteredGroups;

  private final SynchronizedClearableLazy<Set<String>> registeredNotificationIds = new SynchronizedClearableLazy<>(() -> {
    Set<String> result = new HashSet<>();
    NotificationGroupEP.EP_NAME.processWithPluginDescriptor((extension, pluginDescriptor) -> {
      if (extension.notificationIds != null && PluginManagerCore.isDevelopedByJetBrains(pluginDescriptor)) {
        result.addAll(extension.notificationIds);
      }
    });
    return result;
  });

  public NotificationGroupManagerImpl() {
    Map<String, NotificationGroup> registeredGroups = new HashMap<>();
    NotificationGroupEP.EP_NAME.processWithPluginDescriptor((extension, descriptor) -> {
      registerNotificationGroup(extension, descriptor, registeredGroups);
    });
    myRegisteredGroups = new ConcurrentHashMap<>(registeredGroups);

    NotificationGroupEP.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull NotificationGroupEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        registerNotificationGroup(extension, pluginDescriptor, myRegisteredGroups);
        if (extension.notificationIds != null) {
          registeredNotificationIds.drop();
        }
      }

      @Override
      public void extensionRemoved(@NotNull NotificationGroupEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        NotificationGroup group = myRegisteredGroups.get(extension.id);
        if (Objects.equals(group.getPluginId(), pluginDescriptor.getPluginId())) {
          myRegisteredGroups.remove(extension.id);
        }
        if (extension.notificationIds != null) {
          registeredNotificationIds.drop();
        }
      }
    }, null);
  }

  private static void registerNotificationGroup(@NotNull NotificationGroupEP extension,
                                                @NotNull PluginDescriptor pluginDescriptor,
                                                @NotNull Map<String, NotificationGroup> registeredGroups) {
    try {
      String groupId = extension.id;
      NotificationDisplayType type = extension.getDisplayType();
      if (type == null) {
        LOG.warn("Cannot create notification group \"" + groupId + "`\": displayType should be not null");
        return;
      }

      NotificationGroup notificationGroup = NotificationGroup.create(groupId, type, extension.isLogByDefault,
                                                                     extension.toolWindowId, extension.getIcon(pluginDescriptor),
                                                                     extension.getDisplayName(pluginDescriptor),
                                                                     pluginDescriptor.getPluginId());
      notificationGroup.setHideFromSettings(extension.hideFromSettings);
      NotificationGroup old = registeredGroups.put(groupId, notificationGroup);
      if (old != null) {
        LOG.warn("Notification group " + groupId + " is already registered (group=" + old + "). Plugin descriptor: " + pluginDescriptor);
      }
    }
    catch (Exception e) {
      LOG.warn("Cannot create notification group: " + extension, e);
    }
  }

  @Override
  public NotificationGroup getNotificationGroup(@NotNull String groupId) {
    return myRegisteredGroups.get(groupId);
  }

  @Override
  public @NotNull Collection<NotificationGroup> getRegisteredNotificationGroups() {
    return myRegisteredGroups.values();
  }

  @Override
  public boolean isRegisteredNotificationId(@NotNull String notificationId) {
    return registeredNotificationIds.getValue().contains(notificationId);
  }
}
