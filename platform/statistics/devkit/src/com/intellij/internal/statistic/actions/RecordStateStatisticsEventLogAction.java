// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.gdpr.ConsentConfigurable;
import com.intellij.idea.ActionsBundle;
import com.intellij.internal.statistic.StatisticsBundle;
import com.intellij.internal.statistic.eventLog.EventLogFile;
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger;
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import static com.intellij.internal.statistic.eventLog.StatisticsEventLoggerKt.getEventLogProvider;

/**
 * Collects the data from all state collectors and record it in event log.
 *
 * @see ApplicationUsagesCollector
 * @see ProjectUsagesCollector
 */
public class RecordStateStatisticsEventLogAction extends DumbAwareAction {
  private final boolean myShowNotification;

  public RecordStateStatisticsEventLogAction() {
    this(true);
  }

  public RecordStateStatisticsEventLogAction(boolean showNotification) {
    super(ActionsBundle.message("action.RecordStateCollectors.text"),
          ActionsBundle.message("action.RecordStateCollectors.description"),
          AllIcons.Ide.IncomingChangesOn);
    myShowNotification = showNotification;
  }

  private static class Holder {
    private static final FUStateUsagesLogger myStatesLogger = new FUStateUsagesLogger();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    if (!checkLogRecordingEnabled(project, "FUS")) return;

    String message = StatisticsBundle.message("stats.collecting.feature.usages.in.event.log");
    ProgressManager.getInstance().run(new Task.Backgroundable(project, message, false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        FeatureUsageLogger.INSTANCE.rollOver();
        EventLogFile logFile = FeatureUsageLogger.INSTANCE.getConfig().getActiveLogFile();
        VirtualFile logVFile = logFile != null ? LocalFileSystem.getInstance().findFileByIoFile(logFile.getFile()) : null;
        Holder.myStatesLogger.logApplicationStates();
        Holder.myStatesLogger.logProjectStates(project, indicator);

        if (!myShowNotification) return;

        ApplicationManager.getApplication().invokeLater(
          () -> {
            Notification notification = new Notification("FeatureUsageStatistics",
                                                         StatisticsBundle.message("stats.feature.usage.statistics"),
                                                         "Finished collecting and recording events", NotificationType.INFORMATION);
            if (logVFile != null) {
              notification.addAction(NotificationAction.createSimple(
                StatisticsBundle.messagePointer("action.NotificationAction.RecordStateStatisticsEventLogAction.text.show.log.file"), () -> {
                  FileEditorManager.getInstance(project).openFile(logVFile, true);
                }));
            }
            notification.notify(project);
          }
        );
      }
    });
  }

  public static boolean checkLogRecordingEnabled(Project project, String recorderId) {
    if (getEventLogProvider(recorderId).isRecordEnabled()) {
      return true;
    }

    Notification notification = new Notification("FeatureUsageStatistics",
                                                 StatisticsBundle.message("stats.feature.usage.statistics"),
                                                 StatisticsBundle.message("stats.logging.is.disabled"),
                                                 NotificationType.WARNING);
    notification.addAction(NotificationAction.createSimple(StatisticsBundle.messagePointer("stats.enable.data.sharing"), () -> {
      new SingleConfigurableEditor(project, new ConsentConfigurable()).show();
    }));
    notification.notify(project);
    return false;
  }
}
