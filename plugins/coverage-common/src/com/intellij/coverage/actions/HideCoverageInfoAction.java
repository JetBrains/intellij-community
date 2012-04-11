package com.intellij.coverage.actions;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

/**
* User: anna
* Date: 2/14/12
*/
public class HideCoverageInfoAction extends AnAction {
  public HideCoverageInfoAction() {
    super("Hide coverage information", "Hide coverage information", IconLoader.getIcon("/actions/cancel.png"));
  }

  public void actionPerformed(final AnActionEvent e) {
    CoverageDataManager.getInstance(e.getData(PlatformDataKeys.PROJECT)).chooseSuitesBundle(null);
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    presentation.setVisible(ActionPlaces.isToolbarPlace(e.getPlace()));
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project != null) {
      final CoverageSuitesBundle suitesBundle = CoverageDataManager.getInstance(project).getCurrentSuitesBundle();
      presentation.setEnabled(suitesBundle != null);
      presentation.setVisible(suitesBundle != null);
    }
  }
}
