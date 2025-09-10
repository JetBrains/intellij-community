// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.Range;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerProxy;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointManager;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointProxy;
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Internal
public class ToggleBreakpointEnabledAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Collection<XBreakpointProxy> breakpoints = findLineBreakpoints(e);
    for (XBreakpointProxy breakpoint : breakpoints) {
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

  private static @NotNull Set<XBreakpointProxy> findLineBreakpoints(AnActionEvent e) {
    Project project = e.getProject();
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (project == null || editor == null) return Collections.emptySet();
    XBreakpointManagerProxy breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project);
    XLineBreakpointManager lineBreakpointManager = breakpointManager.getLineBreakpointManager();
    Document document = editor.getDocument();
    Collection<Range<Integer>> lineRanges = new ArrayList<>();
    for (Caret caret : editor.getCaretModel().getAllCarets()) {
      lineRanges.add(new Range<>(document.getLineNumber(caret.getSelectionStart()), document.getLineNumber(caret.getSelectionEnd())));
    }

    HashSet<XBreakpointProxy> res = new HashSet<>();
    for (XLineBreakpointProxy breakpoint : lineBreakpointManager.getDocumentBreakpointProxies(document)) {
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
