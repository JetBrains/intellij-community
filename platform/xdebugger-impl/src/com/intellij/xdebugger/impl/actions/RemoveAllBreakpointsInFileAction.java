// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author egor
 */
public class RemoveAllBreakpointsInFileAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (project != null && editor != null) {
      XBreakpointManagerImpl breakpointManager = (XBreakpointManagerImpl)XDebuggerManager.getInstance(project).getBreakpointManager();
      XLineBreakpointManager lineBreakpointManager = breakpointManager.getLineBreakpointManager();
      WriteAction.run(() -> lineBreakpointManager.getDocumentBreakpoints(editor.getDocument()).forEach(breakpointManager::removeBreakpoint));
    }
  }
}
