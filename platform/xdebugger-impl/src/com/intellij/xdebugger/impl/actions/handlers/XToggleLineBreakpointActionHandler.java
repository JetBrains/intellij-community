/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.codeInsight.folding.impl.actions.ExpandRegionAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XToggleLineBreakpointActionHandler extends DebuggerActionHandler {

  private final boolean myTemporary;

  public XToggleLineBreakpointActionHandler(boolean temporary) {
    myTemporary = temporary;
  }

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    XSourcePosition position = XDebuggerUtilImpl.getCaretPosition(project, event.getDataContext());
    if (position == null) return false;

    XLineBreakpointType<?>[] breakpointTypes = XDebuggerUtil.getInstance().getLineBreakpointTypes();
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    for (XLineBreakpointType<?> breakpointType : breakpointTypes) {
      final VirtualFile file = position.getFile();
      final int line = position.getLine();
      if (breakpointType.canPutAt(file, line, project) || breakpointManager.findBreakpointAtLine(breakpointType, file, line) != null) {
        return true;
      }
    }
    return false;
  }

  public void perform(@NotNull final Project project, final AnActionEvent event) {
    XSourcePosition position = XDebuggerUtilImpl.getCaretPosition(project, event.getDataContext());
    if (position == null) return;

    Editor editor = event.getData(CommonDataKeys.EDITOR);

    // for folded text check each line and find out type with the biggest priority
    int lineStart = position.getLine();
    int linesEnd = lineStart;
    if (editor != null) {
      FoldRegion region = FoldingUtil.findFoldRegionStartingAtLine(editor, lineStart);
      if (region != null && !region.isExpanded()) {
        linesEnd = region.getDocument().getLineNumber(region.getEndOffset());
      }
    }

    VirtualFile file = position.getFile();
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    XLineBreakpointType<?>[] lineTypes = XDebuggerUtil.getInstance().getLineBreakpointTypes();
    XLineBreakpointType<?> typeWinner = null;
    int lineWinner = -1;
    for (int line = lineStart; line <= linesEnd; line++) {
      int maxPriority = 0;
      for (XLineBreakpointType<?> type : lineTypes) {
        maxPriority = Math.max(maxPriority, type.getPriority());
        final XLineBreakpoint<? extends XBreakpointProperties> breakpoint = breakpointManager.findBreakpointAtLine(type, file, line);
        if (breakpoint != null && myTemporary && !breakpoint.isTemporary()) {
          breakpoint.setTemporary(true);
        } else if (type.canPutAt(file, line, project) || breakpoint != null) {
          if (typeWinner == null || type.getPriority() > typeWinner.getPriority()) {
            typeWinner = type;
            lineWinner = line;
          }
        }
      }
      // already found max priority type - stop
      if (typeWinner != null && typeWinner.getPriority() == maxPriority) {
        break;
      }
    }

    if (typeWinner != null) {
      XDebuggerUtil.getInstance().toggleLineBreakpoint(project, typeWinner, file, lineWinner, myTemporary);
    }

    ExpandRegionAction.expandRegionAtCaret(project, editor);
    if (editor != null && lineStart != lineWinner) {
      editor.getCaretModel().moveToOffset(editor.getDocument().getLineStartOffset(lineWinner));
    }
  }
}
