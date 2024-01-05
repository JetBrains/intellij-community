// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.notification.*;
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
  public static final NotificationGroup NOTIFICATION_GROUP_ID =
    NotificationGroupManager.getInstance().getNotificationGroup("Vcs Messages");
  public static final NotificationGroup IMPORTANT_ERROR_NOTIFICATION =
    NotificationGroupManager.getInstance().getNotificationGroup("Vcs Important Messages");
  public static final NotificationGroup STANDARD_NOTIFICATION =
    NotificationGroupManager.getInstance().getNotificationGroup("Vcs Notifications");
  public static final NotificationGroup SILENT_NOTIFICATION =
    NotificationGroupManager.getInstance().getNotificationGroup("Vcs Silent Notifications");

  protected final @NotNull Project myProject;

  public static VcsNotifier getInstance(@NotNull Project project) {
    return project.getService(VcsNotifier.class);
  }

  public VcsNotifier(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public Notification notify(@NotNull Notification notification) {
    notification.notify(myProject);
    return notification;
  }

  /**
   * @deprecated use {@link #notifyError(String, String, String)} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public Notification notifyError(@NotificationTitle @NotNull String title,
                                  @NotificationContent @NotNull String message) {
    return notifyError(null, title, message, (NotificationListener)null);
  }

  @NotNull
  public Notification notifyError(@NonNls @Nullable String displayId,
                                  @NotificationTitle @NotNull String title,
                                  @NotificationContent @NotNull String message) {
    return notifyError(displayId, title, message, (NotificationListener)null);
  }

  public Notification notifyError(@NonNls @Nullable String displayId,
                                  @NotificationTitle @NotNull String title,
                                  @NotificationContent @NotNull String message,
                                  boolean showDetailsAction) {
    Notification notification = createNotification(IMPORTANT_ERROR_NOTIFICATION, displayId, title, message, NotificationType.ERROR, null);
    if (showDetailsAction) {
      addShowDetailsAction(myProject, notification);
    }
    return notify(notification);
  }

  /**
   * @deprecated use {@link #notifyError(String, String, String, NotificationListener)} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public Notification notifyError(@NotificationTitle @NotNull String title,
                                  @NotificationContent @NotNull String message,
                                  @Nullable NotificationListener listener) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, null, title, message, NotificationType.ERROR, listener);
  }

  @NotNull
  public Notification notifyError(@NonNls @Nullable String displayId,
                                  @NotificationTitle @NotNull String title,
                                  @NotificationContent @NotNull String message,
                                  @Nullable NotificationListener listener) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, displayId, title, message, NotificationType.ERROR, listener);
  }

  @NotNull
  public Notification notifyError(@NonNls @Nullable String displayId,
                                  @NotificationTitle @NotNull String title,
                                  @NotificationContent @NotNull String message,
                                  NotificationAction... actions) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, displayId, title, message, NotificationType.ERROR, actions);
  }

  @NotNull
  public Notification notifyError(@NonNls @Nullable String displayId,
                                  @NotificationTitle @NotNull String title,
                                  @NotificationContent @NotNull String message,
                                  @Nullable Collection<? extends Exception> errors) {
    return notifyError(displayId, title, buildNotificationMessage(message, errors));
  }

  @NotNull
  public Notification notifyWeakError(@NonNls @Nullable String displayId,
                                      @NotificationContent @NotNull String message) {
    return notify(NOTIFICATION_GROUP_ID, displayId, "", message, NotificationType.ERROR);
  }

  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public Notification notifyWeakError(@NonNls @Nullable String displayId,
                                      @NotificationTitle @NotNull String title,
                                      @NotificationContent @NotNull String message) {
    return notify(NOTIFICATION_GROUP_ID, displayId, title, message, NotificationType.ERROR);
  }

  /**
   * @deprecated use {@link #notifySuccess(String, String, String)} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public Notification notifySuccess(@NotificationContent @NotNull String message) {
    return notify(NOTIFICATION_GROUP_ID, null, "", message, NotificationType.INFORMATION);
  }

  /**
   * @deprecated use {@link #notifySuccess(String, String, String)} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public Notification notifySuccess(@NotificationTitle @NotNull String title,
                                    @NotificationContent @NotNull String message) {
    return notify(NOTIFICATION_GROUP_ID, null, title, message, NotificationType.INFORMATION);
  }

  @NotNull
  public Notification notifySuccess(@NonNls @Nullable String displayId,
                                    @NotificationTitle @NotNull String title,
                                    @NotificationContent @NotNull String message) {
    return notify(NOTIFICATION_GROUP_ID, displayId, title, message, NotificationType.INFORMATION);
  }

  /**
   * @deprecated use {@link #notifySuccess(String, String, String, NotificationListener)} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public Notification notifySuccess(@NotificationTitle @NotNull String title,
                                    @NotificationContent @NotNull String message,
                                    @Nullable NotificationListener listener) {
    return notify(NOTIFICATION_GROUP_ID, null, title, message, NotificationType.INFORMATION, listener);
  }

  @NotNull
  public Notification notifySuccess(@NonNls @Nullable String displayId,
                                    @NotificationTitle @NotNull String title,
                                    @NotificationContent @NotNull String message,
                                    @Nullable NotificationListener listener) {
    return notify(NOTIFICATION_GROUP_ID, displayId, title, message, NotificationType.INFORMATION, listener);
  }

  /**
   * @deprecated use {@link #notifyImportantInfo(String, String, String, NotificationListener)} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public Notification notifyImportantInfo(@NotificationTitle @NotNull String title,
                                          @NotificationContent @NotNull String message,
                                          @Nullable NotificationListener listener) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, null, title, message, NotificationType.INFORMATION, listener);
  }

  @NotNull
  public Notification notifyImportantInfo(@NonNls @Nullable String displayId,
                                          @NotificationTitle @NotNull String title,
                                          @NotificationContent @NotNull String message,
                                          @Nullable NotificationListener listener) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, displayId, title, message, NotificationType.INFORMATION, listener);
  }

  @NotNull
  public Notification notifyImportantInfo(@NonNls @Nullable String displayId,
                                          @NotificationTitle @NotNull String title,
                                          @NotificationContent @NotNull String message) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, displayId, title, message, NotificationType.INFORMATION);
  }

  /**
   * @deprecated use {@link #notifyInfo(String, String, String)} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public Notification notifyInfo(@NotificationTitle @NotNull String title,
                                 @NotificationContent @NotNull String message) {
    return notifyInfo(null, title, message, null);
  }

  @NotNull
  public Notification notifyInfo(@NonNls @Nullable String displayId,
                                 @NotificationTitle @NotNull String title,
                                 @NotificationContent @NotNull String message) {
    return notifyInfo(displayId, title, message, null);
  }

  @NotNull
  public Notification notifyInfo(@NonNls @Nullable String displayId,
                                 @NotificationTitle @NotNull String title,
                                 @NotificationContent @NotNull String message,
                                 @Nullable NotificationListener listener) {
    return notify(NOTIFICATION_GROUP_ID, displayId, title, message, NotificationType.INFORMATION, listener);
  }

  @NotNull
  public Notification notifyMinorWarning(@NonNls @Nullable String displayId,
                                         @NotificationTitle @NotNull String title,
                                         @NotificationContent @NotNull String message) {
    return notifyMinorWarning(displayId, title, message, (NotificationListener)null);
  }

  @NotNull
  public Notification notifyMinorWarning(@NonNls @Nullable String displayId,
                                         @NotificationContent @NotNull String message) {
    return notify(STANDARD_NOTIFICATION, displayId, "", message, NotificationType.WARNING, (NotificationListener)null);
  }

  @NotNull
  public Notification notifyMinorWarning(@NonNls @Nullable String displayId,
                                         @NotificationTitle @NotNull String title,
                                         @NotificationContent @NotNull String message,
                                         NotificationAction... actions) {
    return notify(STANDARD_NOTIFICATION, displayId, title, message, NotificationType.WARNING, actions);
  }

  @NotNull
  public Notification notifyMinorWarning(@NonNls @Nullable String displayId,
                                         @NotificationTitle @NotNull String title,
                                         @NotificationContent @NotNull String message,
                                         @Nullable NotificationListener listener) {
    return notify(STANDARD_NOTIFICATION, displayId, title, message, NotificationType.WARNING, listener);
  }

  /**
   * @deprecated use {@link #notifyWarning(String, String, String)} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public Notification notifyWarning(@NotificationTitle @NotNull String title,
                                    @NotificationContent @NotNull String message) {
    return notify(NOTIFICATION_GROUP_ID, null, title, message, NotificationType.WARNING);
  }

  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public Notification notifyWarning(@NonNls @Nullable String displayId,
                                    @NotificationTitle @NotNull String title,
                                    @NotificationContent @NotNull String message) {
    return notifyWarning(displayId, title, message, new NotificationAction[0]);
  }

  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public Notification notifyWarning(@NonNls @Nullable String displayId,
                                    @NotificationTitle @NotNull String title,
                                    @NotificationContent @NotNull String message,
                                    NotificationAction... actions) {
    return notify(NOTIFICATION_GROUP_ID, displayId, title, message, NotificationType.WARNING, actions);
  }

  @NotNull
  public Notification notifyImportantWarning(@NonNls @Nullable String displayId,
                                             @NotificationTitle @NotNull String title,
                                             @NotificationContent @NotNull String message) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, displayId, title, message, NotificationType.WARNING);
  }

  @NotNull
  public Notification notifyImportantWarning(@NonNls @Nullable String displayId,
                                             @NotificationTitle @NotNull String title,
                                             @NotificationContent @NotNull String message,
                                             @Nullable Collection<? extends Exception> errors) {
    return notifyImportantWarning(displayId, title, buildNotificationMessage(message, errors));
  }

  /**
   * @deprecated use {@link #notifyImportantWarning(String, String, String, NotificationListener)} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public Notification notifyImportantWarning(@NotificationTitle @NotNull String title,
                                             @NotificationContent @NotNull String message,
                                             @Nullable NotificationListener listener) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, null, title, message, NotificationType.WARNING, listener);
  }

  @NotNull
  public Notification notifyImportantWarning(@NonNls @Nullable String displayId,
                                             @NotificationTitle @NotNull String title,
                                             @NotificationContent @NotNull String message,
                                             @Nullable NotificationListener listener) {
    return notify(IMPORTANT_ERROR_NOTIFICATION, displayId, title, message, NotificationType.WARNING, listener);
  }

  @NotNull
  public Notification notifyMinorInfo(@NonNls @Nullable String displayId,
                                      @NotificationTitle @NotNull String title,
                                      @NotificationContent @NotNull String message) {
    return notifyMinorInfo(displayId, false, title, message);
  }

  @NotNull
  public Notification notifyMinorInfo(@NonNls @Nullable String displayId,
                                      @NotificationTitle @NotNull String title,
                                      @NotificationContent @NotNull String message,
                                      NotificationAction... actions) {
    return notify(STANDARD_NOTIFICATION, displayId, title, message, NotificationType.INFORMATION, actions);
  }

  @NotNull
  public Notification notifyMinorInfo(@NonNls @Nullable String displayId,
                                      boolean sticky,
                                      @NotificationTitle @NotNull String title,
                                      @NotificationContent @NotNull String message,
                                      NotificationAction... actions) {
    return notify(sticky ? IMPORTANT_ERROR_NOTIFICATION : STANDARD_NOTIFICATION,
                  displayId, title, message, NotificationType.INFORMATION, actions);
  }

  @NotNull
  public Notification logInfo(@Nullable @NonNls String displayId,
                              @NotificationTitle @NotNull String title,
                              @NotificationContent @NotNull String message) {
    return notify(SILENT_NOTIFICATION, displayId, title, message, NotificationType.INFORMATION);
  }

  public void showNotificationAndHideExisting(@NotNull Notification notificationToShow,
                                              @NotNull Class<? extends Notification> klass) {
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
  private static Notification createNotification(@NotNull NotificationGroup notificationGroup,
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

  @NotNull
  private Notification notify(@NotNull NotificationGroup notificationGroup,
                              @NonNls @Nullable String displayId,
                              @NotificationTitle @NotNull String title,
                              @NotificationContent @NotNull String message,
                              @NotNull NotificationType type,
                              @Nullable NotificationListener listener) {
    Notification notification = createNotification(notificationGroup, displayId, title, message, type, listener);
    return notify(notification);
  }

  @NotNull
  private Notification notify(@NotNull NotificationGroup notificationGroup,
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

  @Nls
  @NotNull
  private static String buildNotificationMessage(@Nls String message,
                                                 @Nullable Collection<? extends Exception> errors) {
    return message.replace(LINE_SEPARATOR, BR) +
           stringifyErrors(errors);
  }

  /**
   * Splits the given VcsExceptions to one string. Exceptions are separated by &lt;br/&gt;
   * Line separator is also replaced by &lt;br/&gt;
   */
  @NotNull
  private static @Nls String stringifyErrors(@Nullable Collection<? extends Exception> errors) {
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
