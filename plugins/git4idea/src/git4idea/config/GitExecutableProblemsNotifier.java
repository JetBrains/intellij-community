// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.ModalityUiUtil;
import git4idea.GitNotificationIdsHolder;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.NoSuchFileException;

import static com.intellij.notification.NotificationsManager.getNotificationsManager;
import static git4idea.config.GitExecutableProblemHandlersKt.findGitExecutableProblemHandler;
import static java.util.Objects.requireNonNullElse;

@Service(Service.Level.PROJECT)
public final class GitExecutableProblemsNotifier {
  public static GitExecutableProblemsNotifier getInstance(@NotNull Project project) {
    return project.getService(GitExecutableProblemsNotifier.class);
  }

  private final @NotNull Project myProject;

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
    ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), () -> {
      if (ensureSingularOfType(project, notification.getClass())) {
        notification.notify(project);
      }
    });
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
                                 @Nullable @NlsContexts.NotificationTitle String title,
                                 @NotNull @NlsContexts.NotificationContent String content,
                                 @NotNull NotificationType type) {
      super(groupDisplayId, requireNonNullElse(title, ""), content, type);
      setDisplayId(GitNotificationIdsHolder.BAD_EXECUTABLE);
    }
  }

  /**
   * Convert validation exception to pretty error message
   */
  public static @Nls @NotNull String getPrettyErrorMessage(@NotNull Throwable exception) {
    String errorMessage = null;
    if (exception instanceof GitVersionIdentificationException) {
      if (exception.getCause() != null) {
        Throwable cause = exception.getCause();
        if (cause instanceof NoSuchFileException) {
          errorMessage = GitBundle.message("git.executable.error.file.not.found", cause.getMessage());
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
        return VcsBundle.message("exception.text.unknown.error");
      }
    }
    return errorMessage;
  }
}
