// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.gdpr.ConsentConfigurable;
import com.intellij.idea.ActionsBundle;
import com.intellij.internal.statistic.StatisticsBundle;
import com.intellij.internal.statistic.eventLog.EventLogFile;
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerKt;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class OpenEventLogFileAction extends DumbAwareAction {
  private final String myRecorderId;

  public OpenEventLogFileAction(String recorderId) {
    super(StatisticsBundle.message("stats.open.0.event.log", recorderId),
          ActionsBundle.message("group.OpenEventLogFileAction.description"),
          AllIcons.FileTypes.Text);
    myRecorderId = recorderId;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    final EventLogFile logFile = StatisticsEventLoggerKt.getEventLogProvider(myRecorderId).getActiveLogFile();
    final VirtualFile logVFile = logFile != null ? LocalFileSystem.getInstance().findFileByIoFile(logFile.getFile()) : null;
    if (logVFile == null) {
      showNotification(project, NotificationType.WARNING, StatisticsBundle.message("stats.there.is.no.active.event.log"));
      return;
    }
    FileEditorManager.getInstance(project).openFile(logVFile, true);
  }

  protected void showNotification(@NotNull Project project,
                                  @NotNull NotificationType type,
                                  @NotNull String message) {
    String title = StatisticsBundle.message("stats.feature.usage.statistics");
    final Notification notification = new Notification("FeatureUsageStatistics", title, message, type);
    notification.addAction(NotificationAction.createSimple(
      StatisticsBundle.messagePointer("stats.enable.data.sharing"), () -> {
      final SingleConfigurableEditor editor = new SingleConfigurableEditor(project, new ConsentConfigurable());
      editor.show();
    }));
    notification.notify(project);
  }
}
