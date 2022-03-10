// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.focus;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.FocusManagerImpl;
import org.jetbrains.annotations.NotNull;

public class LogFocusRequestsAction extends ToggleAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setVisible(SystemInfo.isJetBrainsJvm);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return FocusManagerImpl.FOCUS_REQUESTS_LOG.isDebugEnabled();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    FocusManagerImpl.FOCUS_REQUESTS_LOG.setLevel(state ? LogLevel.DEBUG : LogLevel.INFO);
  }
}
