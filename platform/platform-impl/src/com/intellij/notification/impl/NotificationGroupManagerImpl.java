// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NotificationGroupManagerImpl implements NotificationGroupManager {
  private static final Logger LOG = Logger.getInstance(NotificationGroupManagerImpl.class);
  private final Map<String, NotificationGroup> myRegisteredGroups;
  private final Set<String> myRegisteredNotificationIds = new HashSet<>();

  public NotificationGroupManagerImpl() {
    myRegisteredGroups = new ConcurrentHashMap<>(NotificationGroupEP.EP_NAME.getPoint().size());

    NotificationGroupEP.EP_NAME.processWithPluginDescriptor((extension, descriptor) -> {
      registerNotificationGroup(extension, descriptor);
    });

    NotificationGroupEP.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull NotificationGroupEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        registerNotificationGroup(extension, pluginDescriptor);
      }

      @Override
      public void extensionRemoved(@NotNull NotificationGroupEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        NotificationGroup group = myRegisteredGroups.get(extension.id);
        if (Objects.equals(group.getPluginId(), pluginDescriptor.getPluginId())) {
          myRegisteredGroups.remove(extension.id);
        }
        myRegisteredNotificationIds.removeAll(NotificationCollector.parseIds(extension.notificationIds));
      }
    }, null);
  }

  private void registerNotificationGroup(@NotNull NotificationGroupEP extension, @NotNull PluginDescriptor pluginDescriptor) {
    try {
      String groupId = extension.id;
      NotificationDisplayType type = extension.getDisplayType();
      if (type == null) {
        LOG.warn("Cannot create notification group \"" + groupId + "`\": displayType should be not null");
        return;
      }

      NotificationGroup notificationGroup = NotificationGroup.create(groupId, type, extension.isLogByDefault,
                                                                     extension.toolWindowId, extension.getIcon(),
                                                                     extension.getDisplayName(), pluginDescriptor.getPluginId());
      NotificationGroup old = myRegisteredGroups.put(groupId, notificationGroup);
      if (old != null) {
        LOG.warn("Notification group " + groupId + " is already registered (group=" + old + "). Plugin descriptor: " + pluginDescriptor);
      }

      if (PluginManager.getInstance().isDevelopedByJetBrains(pluginDescriptor)) {
        myRegisteredNotificationIds.addAll(NotificationCollector.parseIds(extension.notificationIds));
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
    return myRegisteredNotificationIds.contains(notificationId);
  }
}
