// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.notification;

import com.intellij.notification.impl.NotificationsToolWindowFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Please use {@code ActionCenter}
 */
@Deprecated
public final class EventLog {
  public static @NotNull LogModel getLogModel(@Nullable Project project) {
    return new LogModel(project);
  }

  public static @Nullable ToolWindow getEventLog(@Nullable Project project) {
    return project == null ? null : ToolWindowManager.getInstance(project).getToolWindow(NotificationsToolWindowFactory.ID);
  }

  public static void toggleLog(final @Nullable Project project, final @Nullable Notification notification) {
    if (project == null) return;
    ActionCenter.toggleLog(project);
  }
}
