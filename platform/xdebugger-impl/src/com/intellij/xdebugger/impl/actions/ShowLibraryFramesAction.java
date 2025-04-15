// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

final class ShowLibraryFramesAction extends ToggleAction implements ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  // we should remember initial answer "isLibraryFrameFilterSupported" because on stop no debugger process, but UI is still shown
  // - we should avoid "jumping" (visible (start) - invisible (stop) - visible (start again))
  private static final String IS_LIBRARY_FRAME_FILTER_SUPPORTED = "isLibraryFrameFilterSupported";

  ShowLibraryFramesAction() {
    super(XDebuggerBundle.message("hide.library.frames.tooltip"), "", AllIcons.General.Filter);
  }

  private static boolean isLibraryFrameFilterSupported(@NotNull AnActionEvent e, Presentation presentation) {
    Object isSupported = presentation.getClientProperty(IS_LIBRARY_FRAME_FILTER_SUPPORTED);
    XDebugSessionProxy sessionProxy = DebuggerUIUtil.getSessionProxy(e);
    if (isSupported == null) {
      if (sessionProxy == null) {
        // if sessionProxy is null and isSupported is null - just return, it means that action created initially not in the xdebugger tab
        presentation.setVisible(false);
        return false;
      }

      isSupported = sessionProxy.isLibraryFrameFilterSupported();
      presentation.putClientProperty(IS_LIBRARY_FRAME_FILTER_SUPPORTED, isSupported);
    }

    return Boolean.TRUE.equals(isSupported);
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    super.update(e);

    Presentation presentation = e.getPresentation();

    if (isLibraryFrameFilterSupported(e, presentation)) {
      presentation.setVisible(true);
      // Change the tooltip of a button in a toolbar and don't change anything for a context menu.
      if (e.isFromActionToolbar()) {
        final boolean shouldShow = !isSelected(e);
        presentation.setText(XDebuggerBundle.message(shouldShow
                                                     ? "hide.library.frames.tooltip"
                                                     : "show.all.frames.tooltip"));
      }
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
    return !XDebuggerSettingManagerImpl.getInstanceImpl().getDataViewSettings().isShowLibraryStackFrames();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean enabled) {
    XDebuggerSettingManagerImpl.getInstanceImpl().getDataViewSettings().setShowLibraryStackFrames(!enabled);
    XDebuggerUtilImpl.rebuildAllSessionsViews(e.getProject());
  }
}