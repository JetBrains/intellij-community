// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions;

import com.intellij.internal.statistic.eventLog.EventLogExternalSettingsService;
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger;
import com.intellij.internal.statistic.service.fus.FUSWhitelist;
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class RecordStateStatisticsEventLogAction extends AnAction {
  private static final FUStateUsagesLogger myStatesLogger = new FUStateUsagesLogger();
  private static final EventLogExternalSettingsService myEventLogSettingsService = EventLogExternalSettingsService.getFeatureUsageSettings();

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Collecting Feature Usages In Event Log", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final String serviceUrl = myEventLogSettingsService.getServiceUrl();
        if (serviceUrl == null) {
          return;
        }

        final FUSWhitelist whitelist = myEventLogSettingsService.getApprovedGroups();
        if (whitelist.isEmpty() && !ApplicationManager.getApplication().isInternal()) {
          return;
        }

        FeatureUsageLogger.INSTANCE.rollOver();
        myStatesLogger.logApplicationStates(whitelist, true);
        myStatesLogger.logProjectStates(project, whitelist, true);

        ApplicationManager.getApplication().invokeLater(
          () -> showNotification(project, "Finished collecting and recording events")
        );
      }
    });
  }

  protected void showNotification(@NotNull Project project, @NotNull String message) {
    Notifications.Bus.notify(new Notification("FeatureUsageStatistics", "Feature Usage Statistics", message, NotificationType.INFORMATION), project);
  }
}
