// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
public final class NotificationsToolWindowNotificationListener implements Notifications {
  private final Project myProject;

  public NotificationsToolWindowNotificationListener() {
    this(null);
  }

  public NotificationsToolWindowNotificationListener(@Nullable Project project) {
    myProject = project;
  }

  @Override
  public void notify(@NotNull Notification notification) {
    NotificationsToolWindowFactory.Companion.addNotification(myProject, notification);
  }
}