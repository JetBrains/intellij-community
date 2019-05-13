// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions;

import com.intellij.internal.statistic.eventLog.EventLogExternalSettingsService;
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class RecordStateStatisticsEventLogAction extends AnAction {
  private static final FUStateUsagesLogger myStatesLogger = new FUStateUsagesLogger();
  private static final EventLogExternalSettingsService myEventLogSettingsService = EventLogExternalSettingsService.getInstance();

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

        final Set<String> approvedGroups = myEventLogSettingsService.getApprovedGroups();
        if (approvedGroups.isEmpty() && !ApplicationManagerEx.getApplicationEx().isInternal()) {
          return;
        }

        myStatesLogger.logApplicationStates(approvedGroups);
        myStatesLogger.logProjectStates(project, approvedGroups);

        ApplicationManager.getApplication().invokeLater(
          () -> showNotification(project, e, "Collecting and recording events was finished")
        );
      }
    });
  }

  protected void showNotification(@NotNull Project project, @NotNull AnActionEvent event, @NotNull String message) {
    JBPopupFactory.getInstance().
      createHtmlTextBalloonBuilder(message, MessageType.INFO, null).
      setFadeoutTime(2000).setDisposable(project).createBalloon().
      show(JBPopupFactory.getInstance().guessBestPopupLocation(event.getDataContext()), Balloon.Position.below);
  }
}
