// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.LightColors;
import org.cef.CefApp;
import org.cef.CefSettings;
import org.cef.misc.CefLog;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.nio.file.Path;
import java.util.Arrays;

public final class JBCefNotifications {
  public static @Nullable EditorNotificationPanel createEditorNotificationPanel(Editor editor, JBCefHealthMonitor.Status status) {
    return switch (status) {
      case UNKNOWN, OK -> null;
      case UNPRIVILEGED_USER_NS_DISABLED -> {
        EditorNotificationPanel panel =
          createEditorNotificationPanel(editor, IdeBundle.message("notification.content.jcef.browser.suspended.text"));
        //noinspection DialogTitleCapitalization
        panel.createActionLabel(IdeBundle.message("notification.content.jcef.enable.browser.button"),
                                () -> JBCefAppArmorUtils.showUnprivilegedUserNamespacesRestrictedDialog(panel));
        yield panel;
      }
      case RUN_UNDER_SUPER_USER ->
        createEditorNotificationPanel(editor, IdeBundle.message("notification.content.jcef.super.user.error.message"));

      case GPU_PROCESS_FAILED ->
        createEditorNotificationPanel(editor, IdeBundle.message("notification.content.jcef.gpu.process.failed.error.message"));
      case STARTUP_TEST_FAILED ->
        createEditorNotificationPanel(editor, JcefBundle.message("notification.jcef.startup_test_failed.title"));
      case CEF_SERVER_DISCONNECTED ->
        createEditorNotificationPanel(editor, JcefBundle.message("notification.jcef.disconnect.title"));
    };
  }

  static @Nullable Component createStubPanel(JBCefHealthMonitor.Status status) {
    return switch (status) {
      case UNKNOWN, OK -> null;
      case UNPRIVILEGED_USER_NS_DISABLED -> createUnprivilegedUserNSStubPanel();
      case RUN_UNDER_SUPER_USER -> createTextComponent(
        IdeBundle.message("notification.content.jcef.super.user.error.message"));
      case GPU_PROCESS_FAILED -> createTextComponent(IdeBundle.message("notification.content.jcef.gpu.process.failed.error.message"));
      case STARTUP_TEST_FAILED -> createTextComponent(JcefBundle.message("notification.jcef.startup_test_failed.title"));
      case CEF_SERVER_DISCONNECTED -> createTextComponent(JcefBundle.message("notification.jcef.disconnect.title"));
    };
  }

  static void showAppArmorNotification() {
    Notification notification =
      JBCefApp.getNotificationGroup()
        .createNotification(
          IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.title"),
          IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.message"),
          NotificationType.WARNING);

    AnAction installProfileAction = JBCefAppArmorUtils.getInstallInstallAppArmorProfileAction(() -> notification.expire());
    if (installProfileAction != null) {
      notification.addAction(installProfileAction);
    }

    notification.addAction(
      NotificationAction.createSimple(
        IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.action.disable.sandbox"),
        () -> {
          RegistryManager.getInstance().get("ide.browser.jcef.sandbox.enable").setValue(false);
          notification.expire();
          ApplicationManager.getApplication().restart();
        })
    );

    notification.addAction(
      NotificationAction.createSimple(
        IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.action.learn.more"),
        () -> {
          // TODO(kharitonov): move to https://intellij-support.jetbrains.com/hc/en-us/sections/201620045-Troubleshooting
          BrowserUtil.browse("https://youtrack.jetbrains.com/articles/JBR-A-11");
        })
    );

    Notifications.Bus.notify(notification);
  }

  static void showClearCache(Path cachePath) {
    // NOTE: called from pooled bg thread.
    Notification notification = JBCefApp.getNotificationGroup().createNotification(
      IdeBundle.message("notification.content.jcef.clearcache.title"),
      IdeBundle.message("notification.content.jcef.clearcache.message", cachePath.toString()),
      NotificationType.WARNING);

    notification.notify(null);
  }

  private static JComponent createUnprivilegedUserNSStubPanel() {
    return JBCefAppArmorUtils.getUnprivilegedUserNamespacesRestrictedStubPanel();
  }

  private static EditorNotificationPanel createEditorNotificationPanel(Editor editor, @Nls String text) {
    EditorNotificationPanel panel = new EditorNotificationPanel(editor, LightColors.YELLOW, null, EditorNotificationPanel.Status.Warning);
    panel.setText(text);
    return panel;
  }

  private static JComponent createTextComponent(@Nls String text) {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.anchor = GridBagConstraints.CENTER;
    c.fill = GridBagConstraints.NONE;
    panel.add(new JLabel(text), c);
    return panel;
  }

  //
  // Notifications for different JCEF runtime problems.
  //

  static void showGPUCrashes(@Nullable JBCefApp.JcefStarter restarter) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Notification notification = JBCefApp.getNotificationGroup().createNotification(
        JcefBundle.message("notification.jcef.gpucrash.title"),
        JcefBundle.message("notification.jcef.gpucrash.message"),
        NotificationType.ERROR);

      if (restarter != null) {
        notification.addAction(
          NotificationAction.createSimple(
            JcefBundle.message("notification.jcef.gpucrash.action.restart_verbose"),
            () -> {
              CefApp.getInstance().dispose();
              restarter.initCefApp(true, false);
            }
          ));

        final boolean isDisabledGPU = SettingsHelper.ChromiumArgs.isDisabledGPU(Arrays.asList(restarter.getCefArgs()));
        if (!isDisabledGPU) {
          notification.addAction(
            NotificationAction.createSimple(
              JcefBundle.message("notification.jcef.gpucrash.action.restart_verbose_and_disabled_GPU"),
              () -> {
                CefApp.getInstance().dispose();
                restarter.initCefApp(true, false, SettingsHelper.ChromiumArgs.DISABLE_GPU);
              }
            ));
        }

        addLogActionsIfNecessary(notification);
      }
      // TODO: add other restartJCEF actions with different chromium-args sets (connected with GPU, like --disable-gpu)
      Notifications.Bus.notify(notification);
    });
  }

  static void showDisconnection(@Nullable JBCefApp.JcefStarter restarter) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final boolean isVerboseJCEFLoggingEnabled = CefLog.GetLogLevel() == CefSettings.LogSeverity.LOGSEVERITY_VERBOSE;
      String notificationText = isVerboseJCEFLoggingEnabled
                                ? JcefBundle.message("notification.jcef.disconnect.message_with_verbose_logging")
                                : JcefBundle.message("notification.jcef.disconnect.message_with_silent_logging");

      Notification notification = JBCefApp.getNotificationGroup().createNotification(
        JcefBundle.message("notification.jcef.disconnect.title"),
        notificationText,
        NotificationType.ERROR);

      if (restarter != null) {
        notification.addAction(
          NotificationAction.createSimple(
            JcefBundle.message("notification.jcef.disconnect.action.restart_verbose"),
            () -> restarter.initCefApp(true, false)
          ));

        if (!restarter.isInProcessJCEFStarted()) {
          // NOTE: probably we should show dlg with ask to restart IDE when it's impossible to restart in-process JCEF without IDE restarting
          // (usually jcef can be launched without jvm restart because CefInitialize wasn't called in default out-of-process mode)
          notification.addAction(
            NotificationAction.createSimple(
              JcefBundle.message("notification.jcef.disconnect.action.restart_in_process_JCEF"),
              () -> restarter.initInProcessCefApp(true))
          );
        }
      }

      addLogActionsIfNecessary(notification);

      Notifications.Bus.notify(notification);
    });
  }

  private static void addLogActionsIfNecessary(Notification notification) {
    final boolean isVerboseJCEFLoggingEnabled = CefLog.GetLogLevel() == CefSettings.LogSeverity.LOGSEVERITY_VERBOSE;
    final AnAction collectLogsAction = isVerboseJCEFLoggingEnabled ? ActionManager.getInstance().getAction("CollectZippedLogs") : null;

    if (collectLogsAction != null)
      notification.addAction(collectLogsAction);

    if (isVerboseJCEFLoggingEnabled)
      notification.addAction(
        NotificationAction.createSimple(
          JcefBundle.message("notification.jcef.disconnect.action.open_issue"),
          () -> BrowserUtil.browse("https://youtrack.jetbrains.com/issue/JBR-10027/Intermittent-crashes-of-cefserver"))
      );
  }

  static void showInternalJcefTestFailed(String errDescription, @Nullable JBCefApp.JcefStarter restarter, int counterGPUCrash, int counterCefServerCrash) {
    // NOTE: StartupTest is started only when JBCefApp is created with [default] out-of-process mode.
    if (counterCefServerCrash > 0) {
      final String crashDescription = "CefServer crashed (" + counterCefServerCrash + " times)";
      if (errDescription != null)
        errDescription += " " + crashDescription;
      else
        errDescription = crashDescription;
    }

    String finalErrDescription = errDescription;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final boolean isVerboseJCEFLoggingEnabled = CefLog.GetLogLevel() == CefSettings.LogSeverity.LOGSEVERITY_VERBOSE;
      final int gpuCrashLimit = Integer.getInteger("ide.browser.jcef.startuptest.gpu_crash_limit", 3);
      final boolean isGPUCrashDetected = counterGPUCrash > gpuCrashLimit;

      final AnAction collectLogsAction = isVerboseJCEFLoggingEnabled ? ActionManager.getInstance().getAction("CollectZippedLogs") : null;
      final String logPath = isVerboseJCEFLoggingEnabled ? CefLog.GetFilePath() : null;

      String notificationText = isGPUCrashDetected
                                ? JcefBundle.message("notification.jcef.startup_test_failed.message_with_silent_logging_with_GPU_crashes", finalErrDescription)
                                : JcefBundle.message("notification.jcef.startup_test_failed.message_with_silent_logging", finalErrDescription);
      if (isVerboseJCEFLoggingEnabled) {
        if (collectLogsAction != null || logPath == null)
          notificationText = JcefBundle.message("notification.jcef.startup_test_failed.message_with_verbose_logging", finalErrDescription);
        else
          notificationText = JcefBundle.message("notification.jcef.startup_test_failed.message_with_verbose_logging_print_log_path", finalErrDescription, logPath);
      }

      Notification notification = JBCefApp.getNotificationGroup().createNotification(
        JcefBundle.message("notification.jcef.startup_test_failed.title"),
        notificationText,
        NotificationType.ERROR);

      if (restarter != null) {
        notification.addAction(
          NotificationAction.createSimple(
            JcefBundle.message("notification.jcef.startup_test_failed.action.restart_JCEF_verbose"),
            () -> restarter.initCefApp(true, false))
        );

        if (!restarter.isInProcessJCEFStarted()) {
          // NOTE: probably we should show dlg with ask to restart IDE when it's impossible to restart in-process JCEF without IDE restarting
          // (usually jcef can be launched without jvm restart because CefInitialize wasn't called in default out-of-process mode)
          notification.addAction(
            NotificationAction.createSimple(
              JcefBundle.message("notification.jcef.startup_test_failed.action.restart_in_process_JCEF"),
              () -> restarter.initInProcessCefApp(true))
          );
        }

        if (isGPUCrashDetected) {
          // TODO: add other restartJCEF actions with different chromium-args sets (connected with GPU, like --disable-gpu)
          final boolean restartWithVerboseLogging = restarter.getCefAppInstanceCount() > 1;
          final String actionText = restartWithVerboseLogging
                                    ? JcefBundle.message("notification.jcef.startup_test_failed.action.restart_JCEF_disabled_gpu_verbose")
                                    : JcefBundle.message("notification.jcef.startup_test_failed.action.restart_JCEF_disabled_gpu");
          notification.addAction(
            NotificationAction.createSimple(
              actionText,
              () -> restarter.initCefApp(restartWithVerboseLogging, false, SettingsHelper.ChromiumArgs.DISABLE_GPU)
            ));
        }
      }

      addLogActionsIfNecessary(notification);

      Notifications.Bus.notify(notification);
    });
  }
}
