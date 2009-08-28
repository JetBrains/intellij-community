/*
 * User: anna
 * Date: 20-Nov-2007
 */
package com.intellij.coverage.actions;

import com.intellij.codeEditor.printing.ExportToHTMLSettings;
import com.intellij.codeInspection.export.ExportToHTMLDialog;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuiteImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;

public class GenerateCoverageReportAction extends AnAction {

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final CoverageSuiteImpl currentSuite = (CoverageSuiteImpl)CoverageDataManager.getInstance(project).getCurrentSuite();

    final ExportToHTMLDialog dialog = new ExportToHTMLDialog(project, true);
    dialog.setTitle("Generate coverage report for: \'" + currentSuite.getPresentableName() + "\'");
    dialog.reset();
    dialog.show();
    if (!dialog.isOK()) return;
    dialog.apply();

    final ExportToHTMLSettings settings = ExportToHTMLSettings.getInstance(project);
    currentSuite.getRunner().generateReport(project, currentSuite.getCoverageDataFileName(), settings.OUTPUT_DIRECTORY, settings.OPEN_IN_BROWSER);
  }

  public void update(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      final Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectJdk();
      if (projectJdk != null) {
        final CoverageSuiteImpl currentSuite = (CoverageSuiteImpl)CoverageDataManager.getInstance(project).getCurrentSuite();
        if (currentSuite != null && currentSuite.getRunner().isHTMLReportSupported()) {
          presentation.setEnabled(true);
        }
      }
    }
  }
}