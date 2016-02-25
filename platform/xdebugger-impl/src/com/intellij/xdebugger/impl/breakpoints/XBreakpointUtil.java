/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.codeInsight.folding.impl.actions.ExpandRegionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointPanelProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.PromiseKt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author nik
 */
public class XBreakpointUtil {
  private XBreakpointUtil() {
  }

  public static <B extends XBreakpoint> String getShortText(B breakpoint) {
    //noinspection unchecked
    return StringUtil.shortenTextWithEllipsis(StringUtil.notNullize(breakpoint.getType().getShortText(breakpoint)), 70, 5);
  }

  public static <B extends XBreakpoint> String getDisplayText(@NotNull B breakpoint) {
    //noinspection unchecked
    return breakpoint.getType().getDisplayText(breakpoint);
  }

  @Nullable
  public static XBreakpointType<?, ?> findType(@NotNull @NonNls String id) {
    for (XBreakpointType breakpointType : getBreakpointTypes()) {
      if (id.equals(breakpointType.getId())) {
        return breakpointType;
      }
    }
    return null;
  }

  public static XBreakpointType<?, ?>[] getBreakpointTypes() {
    return XBreakpointType.EXTENSION_POINT_NAME.getExtensions();
  }

  @NotNull
  public static Pair<GutterIconRenderer, Object> findSelectedBreakpoint(@NotNull final Project project, @NotNull final Editor editor) {
    int offset = editor.getCaretModel().getOffset();
    Document editorDocument = editor.getDocument();

    DebuggerSupport[] debuggerSupports = DebuggerSupport.getDebuggerSupports();
    for (DebuggerSupport debuggerSupport : debuggerSupports) {
      final BreakpointPanelProvider<?> provider = debuggerSupport.getBreakpointPanelProvider();

      final int textLength = editor.getDocument().getTextLength();
      if (offset > textLength) {
        offset = textLength;
      }

      Object breakpoint = provider.findBreakpoint(project, editorDocument, offset);
      if (breakpoint != null) {
        final GutterIconRenderer iconRenderer = provider.getBreakpointGutterIconRenderer(breakpoint);
        return Pair.create(iconRenderer, breakpoint);
      }
    }
    return Pair.create(null, null);
  }

  public static List<BreakpointPanelProvider> collectPanelProviders() {
    List<BreakpointPanelProvider> panelProviders = new ArrayList<BreakpointPanelProvider>();
    for (DebuggerSupport debuggerSupport : DebuggerSupport.getDebuggerSupports()) {
      panelProviders.add(debuggerSupport.getBreakpointPanelProvider());
    }
    Collections.sort(panelProviders, new Comparator<BreakpointPanelProvider>() {
      @Override
      public int compare(BreakpointPanelProvider o1, BreakpointPanelProvider o2) {
        return o2.getPriority() - o1.getPriority();
      }
    });
    return panelProviders;
  }

  @Nullable
  public static DebuggerSupport getDebuggerSupport(Project project, BreakpointItem breakpointItem) {
    DebuggerSupport[] debuggerSupports = DebuggerSupport.getDebuggerSupports();
    List<BreakpointItem> items = new ArrayList<BreakpointItem>();
    for (DebuggerSupport support : debuggerSupports) {
      support.getBreakpointPanelProvider().provideBreakpointItems(project, items);
      if (items.contains(breakpointItem)) {
        return support;
      }
      items.clear();
    }
    return null;
  }

  /**
   * Toggle line breakpoint with editor support:
   * - unfolds folded block on the line
   * - if folded, checks if line breakpoints could be toggled inside folded text
   */
  @NotNull
  public static Promise<XLineBreakpoint> toggleLineBreakpoint(@NotNull Project project,
                                                              @NotNull XSourcePosition position,
                                                              @Nullable Editor editor,
                                                              boolean temporary,
                                                              boolean moveCarret) {
    int lineStart = position.getLine();
    VirtualFile file = position.getFile();
    // for folded text check each line and find out type with the biggest priority
    int linesEnd = lineStart;
    if (editor != null) {
      FoldRegion region = FoldingUtil.findFoldRegionStartingAtLine(editor, lineStart);
      if (region != null && !region.isExpanded()) {
        linesEnd = region.getDocument().getLineNumber(region.getEndOffset());
      }
    }

    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    XLineBreakpointType<?>[] lineTypes = XDebuggerUtil.getInstance().getLineBreakpointTypes();
    XLineBreakpointType<?> typeWinner = null;
    int lineWinner = -1;
    for (int line = lineStart; line <= linesEnd; line++) {
      int maxPriority = 0;
      for (XLineBreakpointType<?> type : lineTypes) {
        maxPriority = Math.max(maxPriority, type.getPriority());
        final XLineBreakpoint<? extends XBreakpointProperties> breakpoint = breakpointManager.findBreakpointAtLine(type, file, line);
        if (breakpoint != null && temporary && !breakpoint.isTemporary()) {
          breakpoint.setTemporary(true);
        }
        else if (type.canPutAt(file, line, project) || breakpoint != null) {
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
      XSourcePosition winPosition = (lineStart == lineWinner) ? position : XSourcePositionImpl.create(file, lineWinner);
      if (winPosition != null) {
        Promise<XLineBreakpoint> res = XDebuggerUtilImpl.toggleAndReturnLineBreakpoint(project, typeWinner, winPosition, temporary, editor);

        if (editor != null && lineStart != lineWinner) {
          int offset = editor.getDocument().getLineStartOffset(lineWinner);
          ExpandRegionAction.expandRegionAtOffset(project, editor, offset);
          if (moveCarret) {
            editor.getCaretModel().moveToOffset(offset);
          }
        }
        return res;
      }
    }

    return PromiseKt.rejectedPromise();
  }
}
