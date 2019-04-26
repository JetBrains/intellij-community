// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class SortValuesToggleAction extends ToggleAction implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    XDebugSession session = e.getData(XDebugSession.DATA_KEY);
    e.getPresentation().setEnabledAndVisible(session != null && !session.getDebugProcess().isValuesCustomSorted());
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return XDebuggerSettingManagerImpl.getInstanceImpl().getDataViewSettings().isSortValues();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    XDebuggerSettingManagerImpl.getInstanceImpl().getDataViewSettings().setSortValues(state);
    XDebuggerUtilImpl.rebuildAllSessionsViews(e.getProject());
  }
}
