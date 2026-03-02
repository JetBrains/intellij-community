// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.editor;

import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.BreakpointArea;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointTypeProxy;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.DocumentUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.settings.ShowBreakpointsOverLineNumbersAction;
import kotlin.Unit;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.Cursor;
import java.util.Objects;

@ApiStatus.Internal
public final class BreakpointPromoterEditorListener implements EditorMouseMotionListener, EditorMouseListener {
  private XSourcePositionImpl myLastPosition = null;
  private Icon myLastIcon = null;
  private boolean myLastInterLine = false;

  private final Project myProject;
  private final XDebuggerLineChangeHandler lineChangeHandler;

  public BreakpointPromoterEditorListener(Project project, CoroutineScope coroutineScope) {
    myProject = project;
    lineChangeHandler = new XDebuggerLineChangeHandler(coroutineScope,
                                                         (gutter, position, breakpointType, breakpointArea) -> {
                                                           onBreakpointTypeResolved(gutter, position, breakpointType, breakpointArea);
                                                           return Unit.INSTANCE;
                                                         });
  }

  @Override
  public void mouseMoved(@NotNull EditorMouseEvent e) {
    var context = getMouseEventContext(e);
    if (context == null) return;
    var editor = context.editor();
    var gutter = context.gutter();
    if (e.getArea() == EditorMouseEventArea.LINE_NUMBERS_AREA && EditorUtil.isBreakPointsOnLineNumbers()) {
      int mouseY = e.getMouseEvent().getY();
      BreakpointArea breakpointArea = EditorUtil.yToLogicalLineWithInterLineDetection(editor, mouseY);
      int line = breakpointArea.getLine();
      boolean isBetweenLines = breakpointArea.isBetweenLines();
      Document document = editor.getDocument();
      if (DocumentUtil.isValidLine(line, document)) {
        XSourcePositionImpl position = XSourcePositionImpl.create(FileDocumentManager.getInstance().getFile(document), line);
        if (position != null) {
          boolean lineChanged = myLastPosition == null ||
                                !myLastPosition.getFile().equals(position.getFile()) ||
                                myLastPosition.getLine() != line ||
                                myLastInterLine != isBetweenLines;

          if (lineChanged) {
            // drop an icon first and schedule the available types calculation
            clear(gutter);
            myLastInterLine = isBetweenLines;
            myLastPosition = position;
            lineChangeHandler.lineChanged(editor, position, breakpointArea);
          }
          return;
        }
      }
    }
    clearOnMouseExit(gutter); // the "mouse has entered another gutter area" case
  }

  @Override
  public void mouseExited(@NotNull EditorMouseEvent e) {
    var context = getMouseEventContext(e);
    if (context == null) return;
    clearOnMouseExit(context.gutter()); // the "mouse has exited the gutter" case
  }

  private @Nullable MouseEventContext getMouseEventContext(@NotNull EditorMouseEvent e) {
    if (!ExperimentalUI.isNewUI() || !ShowBreakpointsOverLineNumbersAction.isSelected()) return null;
    Editor editor = e.getEditor();
    if (editor.getProject() != myProject || editor.getEditorKind() != EditorKind.MAIN_EDITOR) return null;
    EditorGutter editorGutter = editor.getGutter();
    if (!(editorGutter instanceof EditorGutterComponentEx gutter)) return null;
    return new MouseEventContext(editor, gutter);
  }

  private void clearOnMouseExit(@NotNull EditorGutterComponentEx gutter) {
    if (myLastIcon != null) {
      clear(gutter);
      myLastPosition = null;
      myLastInterLine = false;
      lineChangeHandler.exitedGutter();
    }
  }

  private void clear(EditorGutterComponentEx gutter) {
    updateActiveLineNumberIcon(gutter, null, null, null, false);
    myLastIcon = null;
    TooltipController.getInstance().cancelTooltips();
  }
  private void onBreakpointTypeResolved(EditorGutterComponentEx gutter,
                                        XSourcePositionImpl position,
                                        @Nullable XBreakpointTypeProxy breakpointType,
                                        BreakpointArea breakpointArea) {

    if (breakpointType == null) {
      clear(gutter);
      return;
    }

    if (breakpointArea instanceof BreakpointArea.InterLine interLine) {
      myLastIcon = interLine.getConfiguration().getIcon();
      updateActiveLineNumberIcon(gutter, myLastIcon, position.getLine(), interLine.getConfiguration().getHoverTooltip(), true);
      return;
    }

    myLastIcon = breakpointType.getEnabledIcon();
    updateActiveLineNumberIcon(gutter, myLastIcon, position.getLine(),
                               XDebuggerBundle.message("xbreakpoint.add.hover.tooltip"),
                               false);
  }

  private static void updateActiveLineNumberIcon(@NotNull EditorGutterComponentEx gutter,
                                                 @Nullable Icon icon,
                                                 @Nullable Integer line,
                                                 @Nullable @Nls String hoverTooltip,
                                                 boolean isInterLine) {
    if (gutter.getClientProperty("editor.gutter.context.menu") != null) return;

    boolean requireRepaint = false;

    if (gutter.getClientProperty("line.number.hover.icon") != icon) {
      gutter.putClientProperty("line.number.hover.icon", icon);
      gutter.putClientProperty("line.number.hover.icon.context.menu",
                               icon == null ? null : ActionManager.getInstance().getAction("XDebugger.Hover.Breakpoint.Context.Menu"));
      if (icon != null) {
        gutter.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
      requireRepaint = true;
    }

    if (!Objects.equals(gutter.getClientProperty("active.line.number"), line)) {
      gutter.putClientProperty("active.line.number", line);
      requireRepaint = true;
    }

    if (!Objects.equals(gutter.getClientProperty("line.number.hover.between.lines"), isInterLine)) {
      gutter.putClientProperty("line.number.hover.between.lines", isInterLine);
      requireRepaint = true;
    }

    String tooltip = icon == null ? null : hoverTooltip;
    gutter.putClientProperty("line.number.hover.tooltip", tooltip);

    if (requireRepaint) {
      gutter.repaint();
    }
  }

  private record MouseEventContext(@NotNull Editor editor, @NotNull EditorGutterComponentEx gutter) { }
}
