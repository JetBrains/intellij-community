/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  private static Notification createNotification(@NotNull NotificationGroup notificationGroup,
                                                 @NotNull String title, @NotNull String message, @NotNull NotificationType type,
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
  protected Notification notify(@NotNull NotificationGroup notificationGroup, @NotNull String title, @NotNull String message,
                                @NotNull NotificationType type, @Nullable NotificationListener listener) {
    Notification notification = createNotification(notificationGroup, title, message, type, listener);
    notification.notify(myProject);
    return notification;
  }

  @NotNull
  public Notification notifyError(@NotNull String title, @NotNull String message) {
    return notifyError(title, message, null);
  }

  @NotNull
  public Notification notifyError(@NotNull String title, @NotNull String message, @Nullable NotificationListener listener) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.ERROR, listener);
  }

  @NotNull
  public Notification notifyWeakError(@NotNull String message) {
    return notify(NOTIFICATION_GROUP_ID, "", message, NotificationType.ERROR, null);
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
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.INFORMATION, null);
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
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.WARNING, null);
  }

  @NotNull
  public Notification notifyImportantWarning(@NotNull String title, @NotNull String message, @Nullable NotificationListener listener) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, title, message, NotificationType.WARNING, listener);
  }

  @NotNull
  public Notification notifyMinorInfo(@NotNull String title, @NotNull String message) {
    return notifyMinorInfo(title, message, null);
  }

  @NotNull
  public Notification notifyMinorInfo(@NotNull String title, @NotNull String message, @Nullable NotificationListener listener) {
    return notify(STANDARD_NOTIFICATION, title, message, NotificationType.INFORMATION, listener);
  }

  public Notification logInfo(@NotNull String title, @NotNull String message) {
    return notify(SILENT_NOTIFICATION, title, message, NotificationType.INFORMATION, null);
  }
}
