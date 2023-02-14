// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import org.jetbrains.annotations.NotNull;

final class ShowLibraryFramesAction extends ToggleAction {
  // we should remember initial answer "isLibraryFrameFilterSupported" because on stop no debugger process, but UI is still shown
  // - we should avoid "jumping" (visible (start) - invisible (stop) - visible (start again))
  private static final String IS_LIBRARY_FRAME_FILTER_SUPPORTED = "isLibraryFrameFilterSupported";

  private volatile boolean myShouldShow;

  ShowLibraryFramesAction() {
    super("", "", AllIcons.General.Filter);
    myShouldShow = XDebuggerSettingManagerImpl.getInstanceImpl().getDataViewSettings().isShowLibraryStackFrames();
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    super.update(e);

    Presentation presentation = e.getPresentation();

    Object isSupported = presentation.getClientProperty(IS_LIBRARY_FRAME_FILTER_SUPPORTED);
    XDebugSession session = e.getData(XDebugSession.DATA_KEY);
    if (isSupported == null) {
      if (session == null) {
        // if session is null and isSupported is null - just return, it means that action created initially not in the xdebugger tab
        presentation.setVisible(false);
        return;
      }

      isSupported = session.getDebugProcess().isLibraryFrameFilterSupported();
      presentation.putClientProperty(IS_LIBRARY_FRAME_FILTER_SUPPORTED, isSupported);
    }

    if (Boolean.TRUE.equals(isSupported)) {
      presentation.setVisible(true);
      final boolean shouldShow = !Toggleable.isSelected(presentation);
      presentation.setText(XDebuggerBundle.message(shouldShow ? "hide.library.frames.tooltip" : "show.all.frames.tooltip"));
    }
    else {
      presentation.setVisible(false);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return !myShouldShow;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean enabled) {
    myShouldShow = !enabled;
    XDebuggerSettingManagerImpl.getInstanceImpl().getDataViewSettings().setShowLibraryStackFrames(myShouldShow);
    XDebuggerUtilImpl.rebuildAllSessionsViews(e.getProject());
  }
}