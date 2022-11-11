// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.Range;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ToggleBreakpointEnabledAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Collection<XLineBreakpoint> breakpoints = findLineBreakpoints(e);
    for (XLineBreakpoint breakpoint : breakpoints) {
      breakpoint.setEnabled(!breakpoint.isEnabled());
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(!findLineBreakpoints(e).isEmpty());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @NotNull
  private static Set<XLineBreakpoint> findLineBreakpoints(AnActionEvent e) {
    Project project = e.getProject();
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (project == null || editor == null) return Collections.emptySet();
    XBreakpointManagerImpl breakpointManager = (XBreakpointManagerImpl)XDebuggerManager.getInstance(project).getBreakpointManager();
    XLineBreakpointManager lineBreakpointManager = breakpointManager.getLineBreakpointManager();
    Document document = editor.getDocument();
    Collection<Range<Integer>> lineRanges = new ArrayList<>();
    for (Caret caret : editor.getCaretModel().getAllCarets()) {
      lineRanges.add(new Range<>(document.getLineNumber(caret.getSelectionStart()), document.getLineNumber(caret.getSelectionEnd())));
    }

    Collection<XLineBreakpointImpl> breakpoints = lineBreakpointManager.getDocumentBreakpoints(document);
    HashSet<XLineBreakpoint> res = new HashSet<>();
    for (XLineBreakpointImpl breakpoint : breakpoints) {
      int line = breakpoint.getLine();
      for (Range<Integer> range : lineRanges) {
        if (range.isWithin(line)) {
          res.add(breakpoint);
        }
      }
    }
    return res;
  }
}
