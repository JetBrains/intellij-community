// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification;

import com.intellij.ide.IdeBundle;
import com.intellij.notification.impl.NotificationsToolWindowFactory;
import com.intellij.notification.impl.StatusMessage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class ActionCenter {
  public static final String TOOL_WINDOW_ID = NotificationsToolWindowFactory.ID;

  public static final String EVENT_REQUESTOR = "Internal notification event requestor";

  public static final Topic<EventListener> MODEL_CHANGED =
    Topic.create("NOTIFICATION_MODEL_CHANGED", EventListener.class, Topic.BroadcastDirection.NONE);

  public static void fireModelChanged() {
    ApplicationManager.getApplication().getMessageBus().syncPublisher(MODEL_CHANGED).modelChanged();
  }

  @ApiStatus.Internal
  public static @Nullable StatusMessage getStatusMessage(@Nullable Project project) {
    return NotificationsToolWindowFactory.Companion.getStatusMessage(project);
  }

  public static @NotNull List<Notification> getNotifications(@Nullable Project project) {
    return NotificationsToolWindowFactory.Companion.getNotifications(project);
  }

  public static void showNotification(@NotNull Project project) {
    ToolWindow window = getToolWindow(project);
    if (window != null) {
      window.show();
    }
  }

  public static void expireNotifications(@NotNull Project project) {
    for (Notification notification : getNotifications(project)) {
      notification.expire();
    }
  }

  public static @Nullable ToolWindow getToolWindow(@Nullable Project project) {
    return project == null ? null : ToolWindowManager.getInstance(project).getToolWindow(NotificationsToolWindowFactory.ID);
  }

  public static @NotNull @Nls String getToolwindowName() {
    return IdeBundle.message("toolwindow.stripe.Notifications");
  }

  public static void toggleLog(@Nullable Project project) {
    ToolWindow toolWindow = getToolWindow(project);
    if (toolWindow != null) {
      if (toolWindow.isVisible()) {
        toolWindow.hide();
      }
      else {
        toolWindow.activate(null);
      }
    }
  }

  public interface EventListener {
    void modelChanged();
  }
}