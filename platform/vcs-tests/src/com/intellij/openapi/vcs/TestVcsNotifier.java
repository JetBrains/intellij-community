// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.notification.ActionCenter;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class TestVcsNotifier extends VcsNotifier {
  private final List<NotificationGroup> VCS_GROUPS
    = Arrays.asList(NOTIFICATION_GROUP_ID, IMPORTANT_ERROR_NOTIFICATION, STANDARD_NOTIFICATION, SILENT_NOTIFICATION);

  public TestVcsNotifier(@NotNull Project project) {
    super(project);
  }

  public Notification getLastNotification() {
    return ContainerUtil.getLastItem(getNotifications());
  }

  @Nullable
  public Notification findExpectedNotification(@NotNull Notification expectedNotification) {
    return ContainerUtil.find(getNotifications(), notification ->
      expectedNotification.getType().equals(notification.getType()) &&
      expectedNotification.getTitle().equals(notification.getTitle()) &&
      expectedNotification.getContent().equals(notification.getContent())
    );
  }

  @NotNull
  public List<Notification> getNotifications() {
    NotificationGroupManager groupManager = NotificationGroupManager.getInstance();
    List<Notification> allNotifications = ActionCenter.getNotifications(myProject);
    return ContainerUtil.filter(allNotifications, it -> {
      return VCS_GROUPS.contains(groupManager.getNotificationGroup(it.getGroupId()));
    });
  }

  public void cleanup() {
    for (Notification notification : getNotifications()) {
      notification.expire();
    }
  }
}
