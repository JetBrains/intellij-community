// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.notification.ActionCenter;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.List;

public final class TestVcsNotifier extends VcsNotifier {
  public TestVcsNotifier(@NotNull Project project) {
    super(project);
  }

  public Notification getLastNotification() {
    return ContainerUtil.getLastItem(getNotifications());
  }

  public @Nullable Notification findExpectedNotification(@NotNull Notification expectedNotification) {
    return ContainerUtil.find(getNotifications(), notification ->
      expectedNotification.getType().equals(notification.getType()) &&
      expectedNotification.getTitle().equals(notification.getTitle()) &&
      expectedNotification.getContent().equals(notification.getContent())
    );
  }

  public @NotNull @Unmodifiable List<Notification> getNotifications() {
    List<NotificationGroup> vcsGroups
      = Arrays.asList(toolWindowNotification(), importantNotification(), standardNotification(), silentNotification());

    NotificationGroupManager groupManager = NotificationGroupManager.getInstance();
    List<Notification> allNotifications = ActionCenter.getNotifications(myProject);
    return ContainerUtil.filter(allNotifications, it -> {
      return vcsGroups.contains(groupManager.getNotificationGroup(it.getGroupId()));
    });
  }

  public void cleanup() {
    for (Notification notification : getNotifications()) {
      notification.expire();
    }
  }
}
