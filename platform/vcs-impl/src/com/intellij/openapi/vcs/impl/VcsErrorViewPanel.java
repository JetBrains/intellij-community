// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  protected boolean canHideWarnings() {
    return false;
  }
}