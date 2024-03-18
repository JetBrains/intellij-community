// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.coverage.actions;

import com.intellij.codeInspection.export.ExportToHTMLDialog;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class GenerateCoverageReportAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = e.getProject();
    assert project != null;
    final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(project);
    final CoverageSuitesBundle currentSuite = coverageDataManager.getCurrentSuitesBundle();


    final CoverageEngine coverageEngine = currentSuite.getCoverageEngine();
    final ExportToHTMLDialog dialog = coverageEngine.createGenerateReportDialog(project, dataContext, currentSuite);
    dialog.reset();
    if (!dialog.showAndGet()) {
      return;
    }
    dialog.apply();

    coverageEngine.generateReport(project, dataContext, currentSuite);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(false);
    final Project project = e.getProject();
    if (project != null) {
      final CoverageSuitesBundle currentSuite = CoverageDataManager.getInstance(project).getCurrentSuitesBundle();
      if (currentSuite != null) {
        final CoverageEngine coverageEngine = currentSuite.getCoverageEngine();
        if (coverageEngine.isReportGenerationAvailable(project, dataContext, currentSuite)) {
          presentation.setEnabledAndVisible(true);
        }
      }
    }
  }

}
