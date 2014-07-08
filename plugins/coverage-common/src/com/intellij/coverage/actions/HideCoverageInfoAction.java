package com.intellij.coverage.actions;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;

/**
* User: anna
* Date: 2/14/12
*/
public class HideCoverageInfoAction extends AnAction {
  public HideCoverageInfoAction() {
    super("&Hide Coverage Data", "Hide coverage data", AllIcons.Actions.Cancel);
  }

  public void actionPerformed(final AnActionEvent e) {
    CoverageDataManager.getInstance(e.getData(CommonDataKeys.PROJECT)).chooseSuitesBundle(null);
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    presentation.setVisible(ActionPlaces.isToolbarPlace(e.getPlace()));
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      final CoverageSuitesBundle suitesBundle = CoverageDataManager.getInstance(project).getCurrentSuitesBundle();
      presentation.setEnabled(suitesBundle != null);
      presentation.setVisible(suitesBundle != null);
    }
  }
}
