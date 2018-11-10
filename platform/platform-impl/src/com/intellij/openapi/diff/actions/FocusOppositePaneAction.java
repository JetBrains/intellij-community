// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class FocusOppositePaneAction extends AnAction implements DumbAware {
  public FocusOppositePaneAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final DiffPanelImpl diffPanel = DiffPanelImpl.fromDataContext(e.getDataContext());
    assert diffPanel != null;
    diffPanel.focusOppositeSide();
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    final DiffPanelImpl diffPanel = DiffPanelImpl.fromDataContext(e.getDataContext());
    e.getPresentation().setEnabled(diffPanel != null);
  }
}