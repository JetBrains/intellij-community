/*
 * User: anna
 * Date: 20-Nov-2007
 */
package com.intellij.coverage.actions;

import com.intellij.codeInspection.export.ExportToHTMLDialog;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;

public class GenerateCoverageReportAction extends AnAction {

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    assert project != null;
    final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(project);
    final CoverageSuitesBundle currentSuite = coverageDataManager.getCurrentSuitesBundle();


    final CoverageEngine coverageEngine = currentSuite.getCoverageEngine();
    final ExportToHTMLDialog dialog = coverageEngine.createGenerateReportDialog(project, dataContext, currentSuite);
    dialog.reset();
    dialog.show();
    if (!dialog.isOK()) return;
    dialog.apply();

    coverageEngine.generateReport(project, dataContext, currentSuite);
  }

  public void update(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    presentation.setVisible(false);
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      final CoverageSuitesBundle currentSuite = CoverageDataManager.getInstance(project).getCurrentSuitesBundle();
      if (currentSuite != null) {
        final CoverageEngine coverageEngine = currentSuite.getCoverageEngine();
        if (coverageEngine.isReportGenerationAvailable(project, dataContext, currentSuite)) {
          presentation.setEnabled(true);
          presentation.setVisible(true);
        }
      }
    }
  }

}
