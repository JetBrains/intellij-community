// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.notification.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsNotifier {

  public static final NotificationGroup NOTIFICATION_GROUP_ID = NotificationGroup.toolWindowGroup(
    "Vcs Messages", ChangesViewContentManager.TOOLWINDOW_ID);
  public static final NotificationGroup IMPORTANT_ERROR_NOTIFICATION = new NotificationGroup(
    "Vcs Important Messages", NotificationDisplayType.STICKY_BALLOON, true);
  public static final NotificationGroup STANDARD_NOTIFICATION = new NotificationGroup(
    "Vcs Notifications", NotificationDisplayType.BALLOON, true);
  public static final NotificationGroup SILENT_NOTIFICATION = new NotificationGroup(
    "Vcs Silent Notifications", NotificationDisplayType.NONE, true);

  private final @NotNull Project myProject;


  public static VcsNotifier getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, VcsNotifier.class);
  }

  public VcsNotifier(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public static Notification createNotification(@NotNull NotificationGroup notificationGroup,
                                                @NotNull String title,
                                                @NotNull String message,
                                                @NotNull NotificationType type,
                                                @Nullable NotificationListener listener) {
    // title can be empty; message can't be neither null, nor empty
    if (StringUtil.isEmptyOrSpaces(message)) {
      message = title;
      title = "";
    }
    // if both title and message were empty, then it is a problem in the calling code => Notifications engine assertion will notify.
    return notificationGroup.createNotification(title, message, type, listener);
  }

  @NotNull
  public Notification notify(@NotNull NotificationGroup notificationGroup,
                             @NotNull String title,
                             @NotNull String message,
                             @NotNull NotificationType type,
                             @Nullable NotificationListener listener) {
    Notification notification = createNotification(notificationGroup, title, message, type, listener);
    return notify(notification);
  }

  @NotNull
  public Notification notify(@NotNull NotificationGroup notificationGroup,
                             @NotNull String title,
                             @NotNull String message,
                             @NotNull NotificationType type,
                             NotificationAction... actions) {
    Notification notification = createNotification(notificationGroup, title, message, type, null);
    for (NotificationAction action : actions) {
      notification.addAction(action);
    }
    return notify(notification);
  }

  @NotNull
  public Notification notify(@NotNull Notification notification) {
    notification.notify(myProject);
    return notification;
  }

  @NotNull
  public Notification notifyError(@NotNull String title, @NotNull String message) {
    return notifyError(title, message, (NotificationListener)null);
  }

  @NotNull
  public Notification notifyError(@NotNull String title, @NotNull String message, @Nullable NotificationListener listener) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.ERROR, listener);
  }

  @NotNull
  public Notification notifyError(@NotNull String title, @NotNull String message, NotificationAction... actions) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.ERROR, actions);
  }

  @NotNull
  public Notification notifyWeakError(@NotNull String message) {
    return notify(NOTIFICATION_GROUP_ID, "", message, NotificationType.ERROR);
  }

  @NotNull
  public Notification notifySuccess(@NotNull String message) {
    return notifySuccess("", message);
  }

  @NotNull
  public Notification notifySuccess(@NotNull String title, @NotNull String message) {
    return notifySuccess(title, message, null);
  }

  @NotNull
  public Notification notifySuccess(@NotNull String title, @NotNull String message, @Nullable NotificationListener listener) {
    return notify(NOTIFICATION_GROUP_ID, title, message, NotificationType.INFORMATION, listener);
  }

  @NotNull
  public Notification notifyImportantInfo(@NotNull String title, @NotNull String message) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.INFORMATION);
  }

  @NotNull
  public Notification notifyImportantInfo(@NotNull String title, @NotNull String message, @Nullable NotificationListener listener) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.INFORMATION, listener);
  }

  @NotNull
  public Notification notifyInfo(@NotNull String message) {
    return notifyInfo("", message);
  }

  @NotNull
  public Notification notifyInfo(@NotNull String title, @NotNull String message) {
    return notifyInfo(title, message, null);
  }

  @NotNull
  public Notification notifyInfo(@NotNull String title, @NotNull String message, @Nullable NotificationListener listener) {
    return notify(NOTIFICATION_GROUP_ID, title, message, NotificationType.INFORMATION, listener);
  }

  @NotNull
  public Notification notifyMinorWarning(@NotNull String title, @NotNull String message) {
    return notifyMinorWarning(title, message, null);
  }

  @NotNull
  public Notification notifyMinorWarning(@NotNull String title, @NotNull String message, @Nullable NotificationListener listener) {
    return notify(STANDARD_NOTIFICATION, title, message, NotificationType.WARNING, listener);
  }

  @NotNull
  public Notification notifyWarning(@NotNull String title, @NotNull String message) {
    return notifyWarning(title, message, null);
  }

  @NotNull
  public Notification notifyWarning(@NotNull String title, @NotNull String message, @Nullable NotificationListener listener) {
    return notify(NOTIFICATION_GROUP_ID, title, message, NotificationType.WARNING, listener);
  }

  @NotNull
  public Notification notifyImportantWarning(@NotNull String title, @NotNull String message) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.WARNING);
  }

  @NotNull
  public Notification notifyImportantWarning(@NotNull String title, @NotNull String message, @Nullable NotificationListener listener) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.WARNING, listener);
  }

  @NotNull
  public Notification notifyMinorInfo(@NotNull String title, @NotNull String message) {
    return notifyMinorInfo(title, message, (NotificationListener)null);
  }

  @NotNull
  public Notification notifyMinorInfo(@NotNull String title, @NotNull String message, @Nullable NotificationListener listener) {
    return notify(STANDARD_NOTIFICATION, title, message, NotificationType.INFORMATION, listener);
  }

  @NotNull
  public Notification notifyMinorInfo(@NotNull String title, @NotNull String message, NotificationAction... actions) {
    return notify(STANDARD_NOTIFICATION, title, message, NotificationType.INFORMATION, actions);
  }

  public Notification logInfo(@NotNull String title, @NotNull String message) {
    return notify(SILENT_NOTIFICATION, title, message, NotificationType.INFORMATION);
  }
}
