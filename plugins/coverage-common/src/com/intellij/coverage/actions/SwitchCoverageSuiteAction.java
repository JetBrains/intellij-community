package com.intellij.coverage.actions;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;

/**
 * @author ven
 */
public class SwitchCoverageSuiteAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    new CoverageSuiteChooserDialog(project).show();
  }

  public void update(AnActionEvent e) {
    super.update(e);
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    e.getPresentation().setEnabled(project != null);
  }
}
