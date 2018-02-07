// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions;

import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsPersistence;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class PersistFUStatisticsSessionAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Persisting statistics session data", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        String persistedInFile = FUStatisticsPersistence.persistProjectUsages(project);

        String msg = persistedInFile == null ? "Session has NOT been persisted." : "Session has been persisted in file " + FUStatisticsPersistence.getStatisticsSystemCacheDirectory() + "\\" + persistedInFile;
        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showMultilineInputDialog(project,  persistedInFile == null ? "ERROR" : "SUCCESSFULLY PERSISTED", "Statistics Session Persistence Result",
                                                  msg,
                                                null, null), ModalityState.NON_MODAL, project.getDisposed());
      }
    });
  }
}
