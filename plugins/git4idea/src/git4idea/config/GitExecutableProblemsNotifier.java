// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.notification.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.ui.GuiUtils;
import git4idea.GitVcs;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.NoSuchFileException;
import java.util.function.Predicate;

import static com.intellij.CommonBundle.getCancelButtonText;
import static com.intellij.execution.util.ExecUtil.sudoAndGetOutput;
import static com.intellij.notification.NotificationsManager.getNotificationsManager;
import static com.intellij.openapi.ui.Messages.getErrorIcon;
import static com.intellij.openapi.util.text.StringUtil.ELLIPSIS;

public class GitExecutableProblemsNotifier {
  private static final Logger LOG = Logger.getInstance(GitExecutableProblemsNotifier.class);

  public static GitExecutableProblemsNotifier getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GitExecutableProblemsNotifier.class);
  }

  @NotNull private final Project myProject;

  public GitExecutableProblemsNotifier(@NotNull Project project) {
    myProject = project;
  }

  @CalledInAny
  public static void showUnsupportedVersionDialog(@NotNull GitVersion version, @Nullable Project project) {
    GuiUtils.invokeLaterIfNeeded(
      () -> Messages.showWarningDialog(project,
                                       GitBundle
                                         .message("git.executable.validation.error.version.message", GitVersion.MIN.getPresentation()),
                                       GitBundle.message("git.executable.validation.error.version.title", version.getPresentation())),
      ModalityState.defaultModalityState());
  }

  @CalledInAny
  public static void showExecutionErrorDialog(@NotNull Throwable e, @Nullable Project project) {
    GuiUtils.invokeLaterIfNeeded(() -> {
      if (isXcodeLicenseError(e)) {
        XcodeLicenseNotAcceptedNotification.showModalError(project, GitBundle.getString("git.executable.validation.error.xcode.message"));
      }
      else if (isInvalidActiveDeveloperPath(e)) {
        InvalidActiveDeveloperPathNotification.showModalError(project, getPrettyErrorMessage(e));
      }
      else {
        Messages.showErrorDialog(project, getPrettyErrorMessage(e), GitBundle.getString("git.executable.validation.error.start.title"));
      }
    }, ModalityState.defaultModalityState());
  }

  @CalledInAny
  public void notifyUnsupportedVersion(@NotNull GitVersion version) {
    BadGitExecutableNotification notification = new UnsupportedGitVersionNotification(version);
    notification.addConfigureGitActions(myProject);
    notify(notification);
  }

  @CalledInAny
  public void notifyExecutionError(@NotNull Throwable exception) {
    if (isXcodeLicenseError(exception)) {
      notify(new XcodeLicenseNotAcceptedNotification());
    }
    else if (isInvalidActiveDeveloperPath(exception)) {
      notify(new InvalidActiveDeveloperPathNotification(myProject, getPrettyErrorMessage(exception)));
    }
    else {
      BadGitExecutableNotification notification = new ErrorRunningGitNotification(getPrettyErrorMessage(exception));
      notification.addConfigureGitActions(myProject);
      notify(notification);
    }
  }

  private void notify(@NotNull BadGitExecutableNotification notification) {
    GuiUtils.invokeLaterIfNeeded(() -> {
      if (ensureSingularOfType(notification.getClass())) {
        notification.notify(myProject);
      }
    }, ModalityState.defaultModalityState());
  }

  /**
   * Expire all notification except latest and check if there is no notification of this type
   *
   * @param notificationType new notification class
   * @return {@code true} if there's no notification of this type, {@code false} otherwise
   */
  private boolean ensureSingularOfType(@NotNull Class<? extends BadGitExecutableNotification> notificationType) {
    BadGitExecutableNotification[] currentNotifications =
      getNotificationsManager().getNotificationsOfType(BadGitExecutableNotification.class, myProject);
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

  /**
   * Notification about unsupported version
   */
  private static class UnsupportedGitVersionNotification extends BadGitExecutableNotification {
    UnsupportedGitVersionNotification(@NotNull GitVersion unsupportedVersion) {
      super(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION.getDisplayId(), null,
            GitBundle.message("git.executable.validation.error.version.title", unsupportedVersion.getPresentation()),
            null,
            GitBundle.message("git.executable.validation.error.version.message", GitVersion.MIN.getPresentation()),
            NotificationType.WARNING, null);
    }
  }

  /**
   * Notification about not being able to determine version
   */
  private static class ErrorRunningGitNotification extends BadGitExecutableNotification {
    ErrorRunningGitNotification(@NotNull String error) {
      super(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION.getDisplayId(), null,
            GitBundle.getString("git.executable.validation.error.start.title"),
            null,
            error,
            NotificationType.ERROR, null);
    }
  }

  /**
   * Notification about not accepted xcode license
   */
  private static class XcodeLicenseNotAcceptedNotification extends BadGitExecutableNotification {
    XcodeLicenseNotAcceptedNotification() {
      super(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION.getDisplayId(), null,
            GitBundle.getString("git.executable.validation.error.xcode.title"),
            null,
            GitBundle.getString("git.executable.validation.error.xcode.message"),
            NotificationType.ERROR, null);
    }

    static void showModalError(@Nullable Project project, @NotNull String message) {
      Messages.showErrorDialog(project, message, GitBundle.getString("git.executable.validation.error.xcode.title"));
    }
  }

  private static class InvalidActiveDeveloperPathNotification extends BadGitExecutableNotification {
    private static final String TITLE = GitBundle.message("git.executable.validation.error.start.title");

    InvalidActiveDeveloperPathNotification(@NotNull Project project, @NotNull String message) {
      super(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION.getDisplayId(), null, TITLE, null, message, NotificationType.ERROR, null);

      addAction(NotificationAction.createSimpleExpiring("Fix Path", () -> sudoXCodeSelectInstall(project)));
    }

    private static void sudoXCodeSelectInstall(@Nullable Project project) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        try {
          ProcessOutput output = sudoAndGetOutput(new GeneralCommandLine("xcode-select", "--install"),
                                                  "Install XCode Command Line Developer Tools");
          if (!output.getStderr().isEmpty()) {
            showError(project, output.getStderr(), null);
          }
        }
        catch (Exception e) {
          showError(project, e.getMessage(), e);
        }
      }, "Requesting XCode Command Line Developer Tools" + ELLIPSIS, false, project);
    }

    private static void showError(@Nullable Project project, @NotNull String message, @Nullable Exception e) {
      LOG.warn(message, e);
      GuiUtils.invokeLaterIfNeeded(() -> {
          Messages.showErrorDialog(project, message, "Couldn't Install Command Line Tools");
      }, ModalityState.defaultModalityState());
    }

    static void showModalError(@Nullable Project project, @NotNull String message) {
      int answer = Messages.showOkCancelDialog(project, message, TITLE, "Fix Path", getCancelButtonText(), getErrorIcon());
      if (Messages.OK == answer) {
        sudoXCodeSelectInstall(project);
      }
    }
  }

  private abstract static class BadGitExecutableNotification extends Notification {
    BadGitExecutableNotification(@NotNull String groupDisplayId,
                                        @Nullable Icon icon,
                                        @Nullable String title,
                                        @Nullable String subtitle,
                                        @Nullable String content,
                                        @NotNull NotificationType type,
                                        @Nullable NotificationListener listener) {
      super(groupDisplayId, icon, title, subtitle, content, type, listener);
    }

    private void addConfigureGitActions(@NotNull Project project) {
      if (SystemInfo.isWindows) {
        addAction(new BrowseNotificationAction(GitBundle.getString("git.executable.validation.error.action.download"),
                                               GitBundle.getString("git.executable.validation.error.action.download.link")));
      }
      addAction(NotificationAction.createSimple(GitBundle.getString("git.executable.validation.error.action.setting"), () ->
        ShowSettingsUtil.getInstance().showSettingsDialog(project, GitVcs.NAME)));
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

  /**
   * Check if validation failed because the XCode license was not accepted yet
   */
  public static boolean isXcodeLicenseError(@NotNull Throwable exception) {
    return isXcodeError(exception, (message) -> message.contains("Agreeing to the Xcode/iOS license"));
  }

  /**
   * Check if validation failed because the XCode command line tools were not found
   */
  public static boolean isInvalidActiveDeveloperPath(@NotNull Throwable exception) {
    return isXcodeError(exception, (message) -> message.contains("invalid active developer path") && message.contains("xcrun"));
  }

  public static boolean isXcodeError(@NotNull Throwable exception, @NotNull Predicate<String> messageIndicator) {
    String message;
    if (exception instanceof GitVersionIdentificationException) {
      Throwable cause = exception.getCause();
      message = cause != null ? cause.getMessage() : null;
    }
    else {
      message = exception.getMessage();
    }

    return message != null
           && SystemInfo.isMac
           && messageIndicator.test(message);
  }
}
