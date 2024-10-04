// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl;

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class VcsErrorViewPanel extends NewErrorTreeViewPanel {
  VcsErrorViewPanel(Project project) {
    super(project, null);
  }

  @Override
  protected boolean canHideWarnings() {
    return false;
  }
}
