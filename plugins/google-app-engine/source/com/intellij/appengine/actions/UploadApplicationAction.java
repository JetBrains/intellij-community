package com.intellij.appengine.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;

/**
 * @author nik
 */
public class UploadApplicationAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    if (project != null) {
      new UploadApplicationDialog(project).show();
    }
  }
}
