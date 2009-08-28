package com.intellij.openapi.vcs.impl;

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.project.Project;

/**
 * @author yole
*/
class VcsErrorViewPanel extends NewErrorTreeViewPanel {
  public VcsErrorViewPanel(Project project) {
    super(project, null);
  }

  protected boolean canHideWarnings() {
    return false;
  }
}