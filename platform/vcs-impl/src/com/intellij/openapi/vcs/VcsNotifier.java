// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.notification.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.NotificationContent;
import com.intellij.openapi.util.NlsContexts.NotificationTitle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.vcs.console.VcsConsoleTabService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.util.ui.UIUtil.BR;
import static com.intellij.util.ui.UIUtil.LINE_SEPARATOR;

public class VcsNotifier {
  /**
   * @deprecated Use {@link #toolWindowNotification()} instead
   */
  @Deprecated
  public static final NotificationGroup NOTIFICATION_GROUP_ID =
    Cancellation.forceNonCancellableSectionInClassInitializer(() -> toolWindowNotification());

  /**
   * @deprecated Use {@link #importantNotification()} instead
   */
  @Deprecated
  public static final NotificationGroup IMPORTANT_ERROR_NOTIFICATION =
    Cancellation.forceNonCancellableSectionInClassInitializer(() -> importantNotification());

  /**
   * @deprecated Use {@link #standardNotification()} instead
   */
  @Deprecated
  public static final NotificationGroup STANDARD_NOTIFICATION =
    Cancellation.forceNonCancellableSectionInClassInitializer(() -> standardNotification());

  /**
   * @deprecated Use {@link #silentNotification()} instead
   */
  @Deprecated
  public static final NotificationGroup SILENT_NOTIFICATION =
    Cancellation.forceNonCancellableSectionInClassInitializer(() -> silentNotification());


  /**
   * {@link NotificationDisplayType#TOOL_WINDOW} balloon shown near the {@link com.intellij.openapi.wm.ToolWindowId#VCS} toolwindow button
   */
  public static @NotNull NotificationGroup toolWindowNotification() {
    return NotificationGroupManager.getInstance().getNotificationGroup("Vcs Messages");
  }

  /**
   * {@link NotificationDisplayType#BALLOON} notification that is hidden automatically.
   */
  public static @NotNull NotificationGroup standardNotification() {
    return NotificationGroupManager.getInstance().getNotificationGroup("Vcs Notifications");
  }

  /**
   * {@link NotificationDisplayType#STICKY_BALLOON} notification that is NOT hidden automatically on timer
   */
  public static @NotNull NotificationGroup importantNotification() {
    return NotificationGroupManager.getInstance().getNotificationGroup("Vcs Important Notifications");
  }

  /**
   * {@link NotificationDisplayType#NONE} notification, that is visible in 'Notifications' toolwindow, but does not produce a balloon.
   */
  public static @NotNull NotificationGroup silentNotification() {
    return NotificationGroupManager.getInstance().getNotificationGroup("Vcs Silent Notifications");
  }


  protected final @NotNull Project myProject;

  public static VcsNotifier getInstance(@NotNull Project project) {
    return project.getService(VcsNotifier.class);
  }

  public VcsNotifier(@NotNull Project project) {
    myProject = project;
  }

  public @NotNull Notification notify(@NotNull Notification notification) {
    if (myProject.isDisposed()) Logger.getInstance(VcsNotifier.class).warn("Project is already disposed: " + notification);
    notification.notify(myProject);
    return notification;
  }

  /**
   * @deprecated use {@link #notifyError(String, String, String)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull Notification notifyError(@NotificationTitle @NotNull String title,
                                  @NotificationContent @NotNull String message) {
    return notifyError(null, title, message, (NotificationListener)null);
  }

  public @NotNull Notification notifyError(@NonNls @Nullable String displayId,
                                           @NotificationTitle @NotNull String title,
                                           @NotificationContent @NotNull String message) {
    return notifyError(displayId, title, message, (NotificationListener)null);
  }

  public Notification notifyError(@NonNls @Nullable String displayId,
                                  @NotificationTitle @NotNull String title,
                                  @NotificationContent @NotNull String message,
                                  boolean showDetailsAction) {
    Notification notification = createNotification(importantNotification(), displayId, title, message, NotificationType.ERROR, null);
    if (showDetailsAction) {
      addShowDetailsAction(myProject, notification);
    }
    return notify(notification);
  }

  /**
   * @deprecated use {@link #notifyError(String, String, String, NotificationListener)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull Notification notifyError(@NotificationTitle @NotNull String title,
                                  @NotificationContent @NotNull String message,
                                  @Nullable NotificationListener listener) {
    return notify(importantNotification(), null, title, message, NotificationType.ERROR, listener);
  }

  public @NotNull Notification notifyError(@NonNls @Nullable String displayId,
                                           @NotificationTitle @NotNull String title,
                                           @NotificationContent @NotNull String message,
                                           @Nullable NotificationListener listener) {
    return notify(importantNotification(), displayId, title, message, NotificationType.ERROR, listener);
  }

  public @NotNull Notification notifyError(@NonNls @Nullable String displayId,
                                           @NotificationTitle @NotNull String title,
                                           @NotificationContent @NotNull String message,
                                           NotificationAction... actions) {
    return notify(importantNotification(), displayId, title, message, NotificationType.ERROR, actions);
  }

  public @NotNull Notification notifyError(@NonNls @Nullable String displayId,
                                           @NotificationTitle @NotNull String title,
                                           @NotificationContent @NotNull String message,
                                           @Nullable Collection<? extends Exception> errors) {
    return notifyError(displayId, title, buildNotificationMessage(message, errors));
  }

  public @NotNull Notification notifyWeakError(@NonNls @Nullable String displayId,
                                               @NotificationContent @NotNull String message) {
    return notify(toolWindowNotification(), displayId, "", message, NotificationType.ERROR);
  }

  @SuppressWarnings("UnusedReturnValue")
  public @NotNull Notification notifyWeakError(@NonNls @Nullable String displayId,
                                               @NotificationTitle @NotNull String title,
                                               @NotificationContent @NotNull String message) {
    return notify(toolWindowNotification(), displayId, title, message, NotificationType.ERROR);
  }

  /**
   * @deprecated use {@link #notifySuccess(String, String, String)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull Notification notifySuccess(@NotificationContent @NotNull String message) {
    return notify(toolWindowNotification(), null, "", message, NotificationType.INFORMATION);
  }

  /**
   * @deprecated use {@link #notifySuccess(String, String, String)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull Notification notifySuccess(@NotificationTitle @NotNull String title,
                                    @NotificationContent @NotNull String message) {
    return notify(toolWindowNotification(), null, title, message, NotificationType.INFORMATION);
  }

  public @NotNull Notification notifySuccess(@NonNls @Nullable String displayId,
                                             @NotificationTitle @NotNull String title,
                                             @NotificationContent @NotNull String message) {
    return notify(toolWindowNotification(), displayId, title, message, NotificationType.INFORMATION);
  }

  /**
   * @deprecated use {@link #notifySuccess(String, String, String, NotificationListener)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull Notification notifySuccess(@NotificationTitle @NotNull String title,
                                    @NotificationContent @NotNull String message,
                                    @Nullable NotificationListener listener) {
    return notify(toolWindowNotification(), null, title, message, NotificationType.INFORMATION, listener);
  }

  public @NotNull Notification notifySuccess(@NonNls @Nullable String displayId,
                                             @NotificationTitle @NotNull String title,
                                             @NotificationContent @NotNull String message,
                                             @Nullable NotificationListener listener) {
    return notify(toolWindowNotification(), displayId, title, message, NotificationType.INFORMATION, listener);
  }

  /**
   * @deprecated use {@link #notifyImportantInfo(String, String, String, NotificationListener)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull Notification notifyImportantInfo(@NotificationTitle @NotNull String title,
                                          @NotificationContent @NotNull String message,
                                          @Nullable NotificationListener listener) {
    return notify(importantNotification(), null, title, message, NotificationType.INFORMATION, listener);
  }

  public @NotNull Notification notifyImportantInfo(@NonNls @Nullable String displayId,
                                                   @NotificationTitle @NotNull String title,
                                                   @NotificationContent @NotNull String message,
                                                   @Nullable NotificationListener listener) {
    return notify(importantNotification(), displayId, title, message, NotificationType.INFORMATION, listener);
  }

  public @NotNull Notification notifyImportantInfo(@NonNls @Nullable String displayId,
                                                   @NotificationTitle @NotNull String title,
                                                   @NotificationContent @NotNull String message) {
    return notify(importantNotification(), displayId, title, message, NotificationType.INFORMATION);
  }

  /**
   * @deprecated use {@link #notifyInfo(String, String, String)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull Notification notifyInfo(@NotificationTitle @NotNull String title,
                                 @NotificationContent @NotNull String message) {
    return notifyInfo(null, title, message, null);
  }

  public @NotNull Notification notifyInfo(@NonNls @Nullable String displayId,
                                          @NotificationTitle @NotNull String title,
                                          @NotificationContent @NotNull String message) {
    return notifyInfo(displayId, title, message, null);
  }

  public @NotNull Notification notifyInfo(@NonNls @Nullable String displayId,
                                          @NotificationTitle @NotNull String title,
                                          @NotificationContent @NotNull String message,
                                          @Nullable NotificationListener listener) {
    return notify(toolWindowNotification(), displayId, title, message, NotificationType.INFORMATION, listener);
  }

  public @NotNull Notification notifyMinorWarning(@NonNls @Nullable String displayId,
                                                  @NotificationTitle @NotNull String title,
                                                  @NotificationContent @NotNull String message) {
    return notifyMinorWarning(displayId, title, message, (NotificationListener)null);
  }

  public @NotNull Notification notifyMinorWarning(@NonNls @Nullable String displayId,
                                                  @NotificationContent @NotNull String message) {
    return notify(standardNotification(), displayId, "", message, NotificationType.WARNING, (NotificationListener)null);
  }

  public @NotNull Notification notifyMinorWarning(@NonNls @Nullable String displayId,
                                                  @NotificationTitle @NotNull String title,
                                                  @NotificationContent @NotNull String message,
                                                  NotificationAction... actions) {
    return notify(standardNotification(), displayId, title, message, NotificationType.WARNING, actions);
  }

  public @NotNull Notification notifyMinorWarning(@NonNls @Nullable String displayId,
                                                  @NotificationTitle @NotNull String title,
                                                  @NotificationContent @NotNull String message,
                                                  @Nullable NotificationListener listener) {
    return notify(standardNotification(), displayId, title, message, NotificationType.WARNING, listener);
  }

  /**
   * @deprecated use {@link #notifyWarning(String, String, String)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull Notification notifyWarning(@NotificationTitle @NotNull String title,
                                    @NotificationContent @NotNull String message) {
    return notify(toolWindowNotification(), null, title, message, NotificationType.WARNING);
  }

  @SuppressWarnings("UnusedReturnValue")
  public @NotNull Notification notifyWarning(@NonNls @Nullable String displayId,
                                             @NotificationTitle @NotNull String title,
                                             @NotificationContent @NotNull String message) {
    return notifyWarning(displayId, title, message, new NotificationAction[0]);
  }

  @SuppressWarnings("UnusedReturnValue")
  public @NotNull Notification notifyWarning(@NonNls @Nullable String displayId,
                                             @NotificationTitle @NotNull String title,
                                             @NotificationContent @NotNull String message,
                                             NotificationAction... actions) {
    return notify(toolWindowNotification(), displayId, title, message, NotificationType.WARNING, actions);
  }

  public @NotNull Notification notifyImportantWarning(@NonNls @Nullable String displayId,
                                                      @NotificationTitle @NotNull String title,
                                                      @NotificationContent @NotNull String message) {
    return notify(importantNotification(), displayId, title, message, NotificationType.WARNING);
  }

  public @NotNull Notification notifyImportantWarning(@NonNls @Nullable String displayId,
                                                      @NotificationTitle @NotNull String title,
                                                      @NotificationContent @NotNull String message,
                                                      @Nullable Collection<? extends Exception> errors) {
    return notifyImportantWarning(displayId, title, buildNotificationMessage(message, errors));
  }

  /**
   * @deprecated use {@link #notifyImportantWarning(String, String, String, NotificationListener)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull Notification notifyImportantWarning(@NotificationTitle @NotNull String title,
                                             @NotificationContent @NotNull String message,
                                             @Nullable NotificationListener listener) {
    return notify(importantNotification(), null, title, message, NotificationType.WARNING, listener);
  }

  public @NotNull Notification notifyImportantWarning(@NonNls @Nullable String displayId,
                                                      @NotificationTitle @NotNull String title,
                                                      @NotificationContent @NotNull String message,
                                                      @Nullable NotificationListener listener) {
    return notify(importantNotification(), displayId, title, message, NotificationType.WARNING, listener);
  }

  public @NotNull Notification notifyMinorInfo(@NonNls @Nullable String displayId,
                                               @NotificationTitle @NotNull String title,
                                               @NotificationContent @NotNull String message) {
    return notifyMinorInfo(displayId, false, title, message);
  }

  public @NotNull Notification notifyMinorInfo(@NonNls @Nullable String displayId,
                                               @NotificationTitle @NotNull String title,
                                               @NotificationContent @NotNull String message,
                                               NotificationAction... actions) {
    return notify(standardNotification(), displayId, title, message, NotificationType.INFORMATION, actions);
  }

  public @NotNull Notification notifyMinorInfo(@NonNls @Nullable String displayId,
                                               boolean sticky,
                                               @NotificationTitle @NotNull String title,
                                               @NotificationContent @NotNull String message,
                                               NotificationAction... actions) {
    return notify(sticky ? importantNotification() : standardNotification(),
                  displayId, title, message, NotificationType.INFORMATION, actions);
  }

  public @NotNull Notification logInfo(@Nullable @NonNls String displayId,
                                       @NotificationTitle @NotNull String title,
                                       @NotificationContent @NotNull String message) {
    return notify(silentNotification(), displayId, title, message, NotificationType.INFORMATION);
  }

  public void showNotificationAndHideExisting(@NotNull Notification notificationToShow) {
    String displayId = notificationToShow.getDisplayId();
    if (displayId != null ) hideAllNotificationsById(displayId);
    notificationToShow.notify(myProject);
  }

  public void hideAllNotificationsById(@NotNull String displayId) {
    NotificationsManager notificationsManager = NotificationsManager.getNotificationsManager();
    for (Notification notification : notificationsManager.getNotificationsOfType(Notification.class, myProject)) {
      if (displayId.equals(notification.getDisplayId())) notification.expire();
    }
  }

  private static @NotNull Notification createNotification(@NotNull NotificationGroup notificationGroup,
                                                          @NonNls @Nullable String displayId,
                                                          @NotificationTitle @NotNull String title,
                                                          @NotificationContent @NotNull String message,
                                                          @NotNull NotificationType type,
                                                          @Nullable NotificationListener listener) {
    // title can be empty; message can't be neither null, nor empty
    if (StringUtil.isEmptyOrSpaces(message)) {
      message = title;
      title = "";
    }
    // if both title and message were empty, then it is a problem in the calling code => Notifications engine assertion will notify.
    Notification notification = notificationGroup.createNotification(title, message, type);
    if (displayId != null && !displayId.isEmpty()) notification.setDisplayId(displayId);
    if (listener != null) notification.setListener(listener);
    return notification;
  }

  private @NotNull Notification notify(@NotNull NotificationGroup notificationGroup,
                                       @NonNls @Nullable String displayId,
                                       @NotificationTitle @NotNull String title,
                                       @NotificationContent @NotNull String message,
                                       @NotNull NotificationType type,
                                       @Nullable NotificationListener listener) {
    Notification notification = createNotification(notificationGroup, displayId, title, message, type, listener);
    return notify(notification);
  }

  private @NotNull Notification notify(@NotNull NotificationGroup notificationGroup,
                                       @NonNls @Nullable String displayId,
                                       @NotificationTitle @NotNull String title,
                                       @NotificationContent @NotNull String message,
                                       @NotNull NotificationType type,
                                       NotificationAction... actions) {
    Notification notification = createNotification(notificationGroup, displayId, title, message, type, null);
    for (NotificationAction action : actions) {
      notification.addAction(action);
    }
    return notify(notification);
  }

  public static void addShowDetailsAction(@NotNull Project project, @NotNull Notification notification) {
    if (!VcsConsoleTabService.getInstance(project).isConsoleEmpty()) {
      notification.addAction(NotificationAction.createSimple(VcsBundle.message("notification.showDetailsInConsole"), () -> {
        VcsConsoleTabService.getInstance(project).showConsoleTabAndScrollToTheEnd();
      }));
    }
  }

  private static @Nls @NotNull String buildNotificationMessage(@Nls String message,
                                                               @Nullable Collection<? extends Exception> errors) {
    return message.replace(LINE_SEPARATOR, BR) +
           stringifyErrors(errors);
  }

  /**
   * Splits the given VcsExceptions to one string. Exceptions are separated by &lt;br/&gt;
   * Line separator is also replaced by &lt;br/&gt;
   */
  private static @NotNull @Nls String stringifyErrors(@Nullable Collection<? extends Exception> errors) {
    if (errors == null || errors.isEmpty()) {
      return "";
    }
    @Nls StringBuilder content = new StringBuilder();
    for (Exception e : errors) {
      if (e instanceof VcsException vcsException) {
        for (String message : vcsException.getMessages()) {
          content.append(message.replace(LINE_SEPARATOR, BR)).append(BR);
        }
      }
      else {
        content.append(e.getMessage().replace(LINE_SEPARATOR, BR)).append(BR);
      }
    }
    return content.toString();
  }
}
