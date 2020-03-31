// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.GuiUtils;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.NoSuchFileException;

import static com.intellij.notification.NotificationsManager.getNotificationsManager;
import static git4idea.config.GitExecutableProblemHandlersKt.findGitExecutableProblemHandler;

public class GitExecutableProblemsNotifier {

  public static GitExecutableProblemsNotifier getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GitExecutableProblemsNotifier.class);
  }

  @NotNull private final Project myProject;

  public GitExecutableProblemsNotifier(@NotNull Project project) {
    myProject = project;
  }

  @CalledInAny
  public void notifyExecutionError(@NotNull Throwable exception) {
    GitExecutableProblemHandler problemHandler = findGitExecutableProblemHandler(myProject);
    ErrorNotifier errorNotifier = new NotificationErrorNotifier(myProject);
    problemHandler.showError(exception, errorNotifier);
  }

  static void notify(@NotNull Project project, @NotNull BadGitExecutableNotification notification) {
    GuiUtils.invokeLaterIfNeeded(() -> {
      if (ensureSingularOfType(project, notification.getClass())) {
        notification.notify(project);
      }
    }, ModalityState.defaultModalityState());
  }

  /**
   * Expire all notification except latest and check if there is no notification of this type
   *
   * @param notificationType new notification class
   * @return {@code true} if there's no notification of this type, {@code false} otherwise
   */
  private static boolean ensureSingularOfType(@NotNull Project project,
                                              @NotNull Class<? extends BadGitExecutableNotification> notificationType) {
    BadGitExecutableNotification[] currentNotifications =
      getNotificationsManager().getNotificationsOfType(BadGitExecutableNotification.class, project);
    int notificationsCount = currentNotifications.length;
    if (notificationsCount <= 0) {
      return true;
    }

    for (int i = 0; i < notificationsCount - 1; i++) {
      currentNotifications[i].expire();
    }

    BadGitExecutableNotification lastNotification = currentNotifications[notificationsCount - 1];
    if (lastNotification.getClass() != notificationType) {
      lastNotification.expire();
      return true;
    }

    return false;
  }

  public void expireNotifications() {
    for (BadGitExecutableNotification notification : getNotificationsManager()
      .getNotificationsOfType(BadGitExecutableNotification.class, myProject)) {
      notification.expire();
    }
  }

  static class BadGitExecutableNotification extends Notification {
    BadGitExecutableNotification(@NotNull String groupDisplayId,
                                        @Nullable Icon icon,
                                        @Nullable String title,
                                        @Nullable String subtitle,
                                        @Nullable String content,
                                        @NotNull NotificationType type,
                                        @Nullable NotificationListener listener) {
      super(groupDisplayId, icon, title, subtitle, content, type, listener);
    }
  }

  /**
   * Convert validation exception to pretty error message
   */
  @NotNull
  public static String getPrettyErrorMessage(@NotNull Throwable exception) {
    String errorMessage = null;
    if (exception instanceof GitVersionIdentificationException) {
      if (exception.getCause() != null) {
        Throwable cause = exception.getCause();
        if (cause instanceof NoSuchFileException) {
          errorMessage = "File not found: " + cause.getMessage();
        }
        else {
          errorMessage = cause.getMessage();
        }
      }
    }
    if (errorMessage == null) {
      if (exception.getMessage() != null) {
        return exception.getMessage();
      }
      else {
        return exception.getClass().getName();
      }
    }
    return errorMessage;
  }

}
