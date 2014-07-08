/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.Range;
import com.intellij.util.containers.HashSet;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author egor
 */
public class ToggleBreakpointEnabledAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Collection<XLineBreakpoint> breakpoints = findLineBreakpoints(e);
    for (XLineBreakpoint breakpoint : breakpoints) {
      breakpoint.setEnabled(!breakpoint.isEnabled());
    }
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(!findLineBreakpoints(e).isEmpty());
  }

  @NotNull
  private static Set<XLineBreakpoint> findLineBreakpoints(AnActionEvent e) {
    Project project = e.getProject();
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (project == null || editor == null) return Collections.emptySet();
    XBreakpointManagerImpl breakpointManager = (XBreakpointManagerImpl)XDebuggerManager.getInstance(project).getBreakpointManager();
    XLineBreakpointManager lineBreakpointManager = breakpointManager.getLineBreakpointManager();
    Document document = editor.getDocument();
    Collection<Range<Integer>> lineRanges = new ArrayList<Range<Integer>>();
    for (Caret caret : editor.getCaretModel().getAllCarets()) {
      lineRanges.add(new Range<Integer>(document.getLineNumber(caret.getSelectionStart()), document.getLineNumber(caret.getSelectionEnd())));
    }

    Collection<XLineBreakpointImpl> breakpoints = lineBreakpointManager.getDocumentBreakpoints(document);
    HashSet<XLineBreakpoint> res = new HashSet<XLineBreakpoint>();
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
