// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NotificationGroupManagerImpl implements NotificationGroupManager {
  private static final Logger LOG = Logger.getInstance(NotificationGroupManagerImpl.class);
  private final Map<String, NotificationGroup> myRegisteredGroups = new ConcurrentHashMap<>();
  private final Set<String> myRegisteredNotificationIds = new HashSet<>();

  public NotificationGroupManagerImpl() {
    for (NotificationGroupEP extension : NotificationGroupEP.EP_NAME.getExtensionList()) {
      registerNotificationGroup(extension);
    }

    NotificationGroupEP.EP_NAME.addExtensionPointListener(new ExtensionPointListener<NotificationGroupEP>() {
      @Override
      public void extensionAdded(@NotNull NotificationGroupEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        registerNotificationGroup(extension);
      }

      @Override
      public void extensionRemoved(@NotNull NotificationGroupEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        myRegisteredGroups.remove(extension.id);
      }
    }, null);
  }

  private void registerNotificationGroup(NotificationGroupEP extension) {
    try {
      String groupId = extension.id;
      NotificationDisplayType displayType = extension.displayType;
      NotificationGroup notificationGroup = NotificationGroup.create(groupId, displayType, extension.isLogByDefault,
                                                                     extension.toolWindowId, extension.getIcon(),
                                                                     extension.getDisplayName(),
                                                                     extension.getPluginDescriptor().getPluginId());
      if (myRegisteredGroups.containsKey(groupId)) {
        LOG.warn("Notification group " + groupId + " is already registered. Plugin descriptor: " + extension.getPluginDescriptor());
      }
      myRegisteredGroups.put(groupId, notificationGroup);
      myRegisteredNotificationIds.addAll(NotificationCollector.parseIds(extension.notificationIds));
    }
    catch (Exception e) {
      LOG.warn("Enable to create notification group: " + extension.toString(), e);
    }
  }

  @Override
  public @NotNull NotificationGroup requireNotificationGroup(@NotNull String groupId) {
    NotificationGroup group = myRegisteredGroups.get(groupId);
    if (group == null) {
      throw new IllegalArgumentException("Notification group `" + groupId + "` is not registered in plugin.xml file");
    }
    return group;
  }

  @Override
  public @Nullable NotificationGroup getNotificationGroup(@NotNull String groupId) {
    return myRegisteredGroups.get(groupId);
  }

  @Override
  public Collection<NotificationGroup> getRegisteredNotificationGroups() {
    return myRegisteredGroups.values();
  }

  @Override
  public boolean isRegisteredNotificationId(@NotNull String notificationId) {
    return myRegisteredNotificationIds.contains(notificationId);
  }

  @Override
  public boolean isRegisteredNotificationGroup(@NotNull String notificationId) {
    return myRegisteredGroups.containsKey(notificationId);
  }
}