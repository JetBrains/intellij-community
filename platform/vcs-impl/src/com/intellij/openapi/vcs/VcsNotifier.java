// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.notification.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.NotificationContent;
import com.intellij.openapi.util.NlsContexts.NotificationTitle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
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
  public Notification notify(@NotNull Notification notification) {
    notification.notify(myProject);
    return notification;
  }

  @NotNull
  public Notification notifyError(@NotificationTitle @NotNull String title, @NotificationContent @NotNull String message) {
    return notifyError(title, message, (NotificationListener)null);
  }

  public Notification notifyError(
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    boolean showDetailsAction
  ) {
    if (showDetailsAction && ProjectLevelVcsManager.getInstance(myProject).isConsoleVisible()) {
      return notifyError(title, message, createShowDetailsAction());
    }
    else {
      return notifyError(title, message);
    }
  }

  @NotNull
  public Notification notifyError(
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    @Nullable NotificationListener listener
  ) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.ERROR, listener);
  }

  @NotNull
  public Notification notifyError(
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    NotificationAction... actions
  ) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.ERROR, actions);
  }

  @NotNull
  public Notification notifyWeakError(@NotificationContent @NotNull String message) {
    return notifyWeakError("", message);
  }

  @NotNull
  public Notification notifyWeakError(@NotificationTitle @NotNull String title, @NotificationContent @NotNull String message) {
    return notify(NOTIFICATION_GROUP_ID, title, message, NotificationType.ERROR);
  }

  @NotNull
  public Notification notifySuccess(@NotificationContent @NotNull String message) {
    return notifySuccess("", message);
  }

  @NotNull
  public Notification notifySuccess(@NotificationTitle @NotNull String title, @NotificationContent @NotNull String message) {
    return notifySuccess(title, message, null);
  }

  @NotNull
  public Notification notifySuccess(
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    @Nullable NotificationListener listener
  ) {
    return notify(NOTIFICATION_GROUP_ID, title, message, NotificationType.INFORMATION, listener);
  }

  @NotNull
  public Notification notifyImportantInfo(@NotificationTitle @NotNull String title, @NotificationContent @NotNull String message) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.INFORMATION);
  }

  @NotNull
  public Notification notifyImportantInfo(
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    @Nullable NotificationListener listener
  ) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.INFORMATION, listener);
  }

  @NotNull
  public Notification notifyInfo(@NotificationContent @NotNull String message) {
    return notifyInfo("", message);
  }

  @NotNull
  public Notification notifyInfo(@NotificationTitle @NotNull String title, @NotificationContent @NotNull String message) {
    return notifyInfo(title, message, null);
  }

  @NotNull
  public Notification notifyInfo(
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    @Nullable NotificationListener listener
  ) {
    return notify(NOTIFICATION_GROUP_ID, title, message, NotificationType.INFORMATION, listener);
  }

  @NotNull
  public Notification notifyMinorWarning(@NotificationTitle @NotNull String title, @NotificationContent @NotNull String message) {
    return notifyMinorWarning(title, message, null);
  }

  @NotNull
  public Notification notifyMinorWarning(
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    @Nullable NotificationListener listener
  ) {
    return notify(STANDARD_NOTIFICATION, title, message, NotificationType.WARNING, listener);
  }

  @NotNull
  public Notification notifyWarning(@NotificationTitle @NotNull String title, @NotificationContent @NotNull String message) {
    return notifyWarning(title, message, null);
  }

  @NotNull
  public Notification notifyWarning(
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    @Nullable NotificationListener listener
  ) {
    return notify(NOTIFICATION_GROUP_ID, title, message, NotificationType.WARNING, listener);
  }

  @NotNull
  public Notification notifyImportantWarning(@NotificationTitle @NotNull String title, @NotificationContent @NotNull String message) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.WARNING);
  }

  @NotNull
  public Notification notifyImportantWarning(@Nls @NotNull String title, @Nls @NotNull String message, NotificationAction... actions) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.WARNING, actions);
  }

  @NotNull
  public Notification notifyImportantWarning(
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    @Nullable NotificationListener listener
  ) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.WARNING, listener);
  }

  @NotNull
  public Notification notifyMinorInfo(@NotificationTitle @NotNull String title, @NotificationContent @NotNull String message) {
    return notifyMinorInfo(title, message, (NotificationListener)null);
  }

  @NotNull
  public Notification notifyMinorInfo(
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    @Nullable NotificationListener listener
  ) {
    return notify(STANDARD_NOTIFICATION, title, message, NotificationType.INFORMATION, listener);
  }

  @NotNull
  public Notification notifyMinorInfo(
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    NotificationAction... actions
  ) {
    return notify(STANDARD_NOTIFICATION, title, message, NotificationType.INFORMATION, actions);
  }

  @NotNull
  public Notification notifyMinorInfo(
    boolean sticky,
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    NotificationAction... actions
  ) {
    return notifyMinorInfo(sticky, null, title, message, actions);
  }

  @NotNull
  public Notification notifyMinorInfo(
    boolean sticky,
    @NonNls @Nullable String notificationDisplayId,
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    NotificationAction... actions
  ) {
    return notify(sticky ? IMPORTANT_ERROR_NOTIFICATION : STANDARD_NOTIFICATION, notificationDisplayId, title, message, NotificationType.INFORMATION, actions);
  }

  public Notification logInfo(@NotificationTitle @NotNull String title, @NotificationContent @NotNull String message) {
    return notify(SILENT_NOTIFICATION, title, message, NotificationType.INFORMATION);
  }

  public void showNotificationAndHideExisting(@NotNull Notification notificationToShow, @NotNull Class<? extends Notification> klass) {
    hideAllNotificationsByType(klass);
    notificationToShow.notify(myProject);
  }

  public void hideAllNotificationsByType(@NotNull Class<? extends Notification> klass) {
    NotificationsManager notificationsManager = NotificationsManager.getNotificationsManager();
    for (Notification notification : notificationsManager.getNotificationsOfType(klass, myProject)) {
      notification.expire();
    }
  }

  @NotNull
  private static Notification createNotification(
    @NotNull NotificationGroup notificationGroup,
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    @NotNull NotificationType type,
    @Nullable NotificationListener listener,
    @Nullable String notificationDisplayId
  ) {
    // title can be empty; message can't be neither null, nor empty
    if (StringUtil.isEmptyOrSpaces(message)) {
      message = title;
      title = "";
    }
    // if both title and message were empty, then it is a problem in the calling code => Notifications engine assertion will notify.
    return notificationGroup.createNotification(title, message, type, listener, notificationDisplayId);
  }

  @NotNull
  private Notification notify(
    @NotNull NotificationGroup notificationGroup,
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    @NotNull NotificationType type,
    @Nullable NotificationListener listener
  ) {
    Notification notification = createNotification(notificationGroup, title, message, type, listener, null);
    return notify(notification);
  }

  @NotNull
  private Notification notify(
    @NotNull NotificationGroup notificationGroup,
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    @NotNull NotificationType type,
    NotificationAction... actions
  ) {
    return notify(notificationGroup, null, title, message, type, actions);
  }

  @NotNull
  private Notification notify(
    @NotNull NotificationGroup notificationGroup,
    @NonNls @Nullable String notificationDisplayId,
    @NotificationTitle @NotNull String title, @NotificationContent @NotNull String message,
    @NotNull NotificationType type,
    NotificationAction... actions
  ) {
    Notification notification = createNotification(notificationGroup, title, message, type, null, notificationDisplayId);
    for (NotificationAction action : actions) {
      notification.addAction(action);
    }
    return notify(notification);
  }

  @NotNull
  private NotificationAction createShowDetailsAction() {
    return NotificationAction.createSimple(
      VcsBundle.message("notification.showDetailsInConsole"),
      () -> {
        ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
        vcsManager.showConsole(vcsManager::scrollConsoleToTheEnd);
      }
    );
  }
}
