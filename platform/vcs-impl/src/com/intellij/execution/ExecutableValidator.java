// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.CommonBundle;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Validates the given external executable. If it is not valid, shows notification to fix it.
 * Notification group is registered as a {@link NotificationDisplayType#STICKY_BALLOON} by default.
 *
 * @author Kirill Likhodedov
 */
public abstract class ExecutableValidator {
  public static final int TIMEOUT_MS = Registry.intValue("vcs.executable.validator.timeout.sec", 60) * 1000;

  private static final Logger LOG = Logger.getInstance(ExecutableValidator.class);
  private static final NotificationGroup ourNotificationGroup = new NotificationGroup("External Executable Critical Failures",
                                                                                      NotificationDisplayType.STICKY_BALLOON, true);
  @NotNull protected final Project myProject;

  @NotNull private final @NlsContexts.DialogTitle String myNotificationErrorTitle;
  @NotNull private final @NlsContexts.DialogMessage String myNotificationErrorDescription;

  /**
   * Configures notification and dialog by setting text messages and titles specific to the whoever uses the validator.
   * @param notificationErrorTitle       title of the notification about not valid executable.
   * @param notificationErrorDescription description of this notification with a link to fix it (link action is defined by
   *                                     {@link #showSettingsAndExpireIfFixed(Notification)}
   */
  public ExecutableValidator(@NotNull Project project, @NotNull @NlsContexts.DialogTitle String notificationErrorTitle,
                             @NotNull @NlsContexts.DialogMessage String notificationErrorDescription) {
    myProject = project;
    myNotificationErrorTitle = notificationErrorTitle;
    myNotificationErrorDescription = notificationErrorDescription;
  }

  protected abstract String getCurrentExecutable();

  /**
   * @return the settings configurable display name, where the executable is shown and can be fixed.
   *         This configurable will be opened if user presses "Fix" on the notification about invalid executable.
   */
  @NotNull @Nls
  protected abstract String getConfigurableDisplayName();

  @Nullable
  protected Notification validate(@NotNull String executable) {
    return !isExecutableValid(executable) ? createDefaultNotification() : null;
  }

  @NotNull
  protected ExecutableNotValidNotification createDefaultNotification() {
    return new ExecutableNotValidNotification();
  }

  /**
   * Returns true if the supplied executable is valid.
   * Default implementation: try to execute the given executable and test if output returned errors.
   * This can take a long time since it spawns external process.
   * @param executable Path to executable.
   * @return true if process with the supplied executable completed without errors and with exit code 0.
   */
  protected boolean isExecutableValid(@NotNull String executable) {
    return doCheckExecutable(executable, Collections.emptyList(), Collections.emptyMap());
  }

  protected static boolean doCheckExecutable(@NotNull String executable, @NotNull List<String> processParameters, @NotNull Map<String, String> envVariables) {
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setExePath(executable);
      commandLine.addParameters(processParameters);
      commandLine.setCharset(CharsetToolkit.getDefaultSystemCharset());
      commandLine.withEnvironment(envVariables);
      CapturingProcessHandler handler = new CapturingProcessHandler(commandLine);
      ProcessOutput result = handler.runProcess(TIMEOUT_MS);
      boolean timeout = result.isTimeout();
      int exitCode = result.getExitCode();
      String stderr = result.getStderr();
      if (timeout) {
        LOG.warn("Validation of " + executable + " failed with a timeout");
      }
      if (exitCode != 0) {
        LOG.warn("Validation of " + executable + " failed with a non-zero exit code: " + exitCode);
      }
      if (!stderr.isEmpty()) {
        LOG.warn("Validation of " + executable + " failed with a non-empty error output: " + stderr);
      }
      return !timeout && exitCode == 0 && stderr.isEmpty();
    }
    catch (Throwable t) {
      LOG.warn(t);
      return false;
    }
  }

  /**
   * Shows a notification about not configured executable with a link to the Settings to fix it.
   * Expires the notification if user fixes the path from the opened Settings dialog.
   * Makes sure that there is always only one notification about the problem in the stack of notifications.
   */
  private void showExecutableNotConfiguredNotification(@NotNull Notification notification) {
    if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return;
    }

    LOG.info("Executable is not valid: " + getCurrentExecutable());
    if (NotificationsManager.getNotificationsManager().getNotificationsOfType(notification.getClass(), myProject).length == 0) { // show only once
      notification.notify(myProject.isDefault() ? null : myProject);
    }
  }

  @NotNull
  protected @NlsContexts.NotificationContent String prepareDescription(@NotNull @Nls String description, boolean appendFixIt) {
    @NlsSafe String executable = getCurrentExecutable();

    HtmlBuilder builder = new HtmlBuilder();

    if (executable.isEmpty()) {
      builder.append(HtmlChunk.raw(myNotificationErrorTitle).bold()).append(HtmlChunk.raw(description));
    }
    else {
      builder
        .append(HtmlChunk.raw(myNotificationErrorTitle).bold())
        .append(" ")
        .append(HtmlChunk.text(executable).bold())
        .append(HtmlChunk.br())
        .append(HtmlChunk.raw(description));
    }
    if (appendFixIt) {
      builder.append(" ").appendLink("", IdeBundle.message("button.fix.it"));
    }

    return builder.toString();
  }

  protected void showSettingsAndExpireIfFixed(@NotNull Notification notification) {
    showSettings();
    if (validate(getCurrentExecutable()) == null) {
      notification.expire();
    }
  }

  protected void showSettings() {
    ShowSettingsUtil.getInstance().showSettingsDialog(myProject, getConfigurableDisplayName());
  }

  /**
   * Checks if executable is valid and displays the notification if not.
   * @return true if executable was valid, false - if not valid (and notification was shown in that case).
   * @see #checkExecutableAndShowMessageIfNeeded(java.awt.Component)
   */
  public boolean checkExecutableAndNotifyIfNeeded() {
    if (myProject.isDisposed()) {
      return false;
    }
    Notification notification = validate(getCurrentExecutable());

    return notify(notification);
  }

  protected boolean notify(@Nullable Notification notification) {
    if (notification != null) {
      showExecutableNotConfiguredNotification(notification);
      return false;
    }
    return true;
  }

  /**
   * Checks if executable is valid and shows the message if not.
   * This method is to be used instead of {@link #checkExecutableAndNotifyIfNeeded()} when Git fails to start from a modal dialog:
   * in that case user won't be able to click "Fix it".
   * @return true if executable was valid, false - if not valid (and a message is shown in that case).
   * @see #checkExecutableAndNotifyIfNeeded()
   */
  public boolean checkExecutableAndShowMessageIfNeeded(@Nullable Component parentComponent) {
    if (myProject.isDisposed()) {
      return false;
    }

    if (!isExecutableValid(getCurrentExecutable())) {
      if (Messages.OK == showMessage(parentComponent)) {
        ApplicationManager.getApplication().invokeLater(() -> showSettings());
      }
      return false;
    }
    return true;
  }

  private int showMessage(@Nullable Component parentComponent) {
    String okText = IdeBundle.message("button.fix.it");
    String cancelText = CommonBundle.getCancelButtonText();
    Icon icon = Messages.getErrorIcon();
    String title = myNotificationErrorTitle;
    String description = myNotificationErrorDescription;
    return parentComponent != null
           ? Messages.showOkCancelDialog(parentComponent, description, title, okText, cancelText, icon)
           : Messages.showOkCancelDialog(myProject, description, title, okText, cancelText, icon);
  }

  public boolean isExecutableValid() {
    return isExecutableValid(getCurrentExecutable());
  }

  public class ExecutableNotValidNotification extends Notification {

    public ExecutableNotValidNotification() {
      this(myNotificationErrorDescription);
    }

    public ExecutableNotValidNotification(@NotNull @Nls String description) {
      this(prepareDescription(description, true), NotificationType.ERROR);
    }

    public ExecutableNotValidNotification(@NotNull @NlsContexts.NotificationContent String preparedDescription, @NotNull NotificationType type) {
      super(ourNotificationGroup.getDisplayId(), "", preparedDescription, type, new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          showSettingsAndExpireIfFixed(notification);
        }
      });
    }
  }
}