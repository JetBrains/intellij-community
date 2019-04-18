// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public abstract class CvsGlobalAction extends AnAction implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    final CvsContext cvsContext = CvsContextWrapper.createInstance(e);
    final Presentation presentation = e.getPresentation();
    if (cvsContext.cvsIsActive()) {
      presentation.setEnabledAndVisible(true);
    }
    else {
      presentation.setEnabledAndVisible(false);
    }
  }
}