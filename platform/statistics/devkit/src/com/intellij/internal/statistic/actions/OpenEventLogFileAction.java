// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions;

import com.intellij.ide.gdpr.ConsentConfigurable;
import com.intellij.internal.statistic.eventLog.EventLogFile;
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class OpenEventLogFileAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    final EventLogFile logFile = FeatureUsageLogger.INSTANCE.getConfig().getActiveLogFile();
    final VirtualFile logVFile = logFile != null ? LocalFileSystem.getInstance().findFileByIoFile(logFile.getFile()) : null;
    if (logVFile == null) {
      showNotification(
        project, NotificationType.WARNING,
        "There's no active event log file. Please enable logging and restart IDE");
      return;
    }
    FileEditorManager.getInstance(project).openFile(logVFile, true);
  }

  protected void showNotification(@NotNull Project project,
                                  @NotNull NotificationType type,
                                  @NotNull String message) {
    final Notification notification = new Notification("FeatureUsageStatistics", "Feature Usage Statistics", message, type);
    notification.addAction(NotificationAction.createSimple("Enable Event Log", () -> {
      final SingleConfigurableEditor editor = new SingleConfigurableEditor(project, new ConsentConfigurable());
      editor.show();
    }));
    notification.notify(project);
  }
}
