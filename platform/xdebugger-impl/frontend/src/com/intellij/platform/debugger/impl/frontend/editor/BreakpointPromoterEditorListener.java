// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.editor;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.DocumentUtil;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.settings.ShowBreakpointsOverLineNumbersAction;
import kotlin.Unit;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

@ApiStatus.Internal
public final class BreakpointPromoterEditorListener implements EditorMouseMotionListener {
  private XSourcePositionImpl myLastPosition = null;
  private Icon myLastIcon = null;

  private final Project myProject;
  private final XDebuggerLineChangeHandler lineChangeHandler;

  public BreakpointPromoterEditorListener(Project project, CoroutineScope coroutineScope) {
    myProject = project;
    lineChangeHandler = new XDebuggerLineChangeHandler(coroutineScope, (gutter, position, icon) -> {
      myLastIcon = icon;
      if (myLastIcon != null) {
        updateActiveLineNumberIcon(gutter, myLastIcon, position.getLine());
      }
      return Unit.INSTANCE;
    });
  }

  @Override
  public void mouseMoved(@NotNull EditorMouseEvent e) {
    if (!ExperimentalUI.isNewUI() || !ShowBreakpointsOverLineNumbersAction.isSelected()) return;
    Editor editor = e.getEditor();
    if (editor.getProject() != myProject || editor.getEditorKind() != EditorKind.MAIN_EDITOR) return;
    EditorGutter editorGutter = editor.getGutter();
    if (editorGutter instanceof EditorGutterComponentEx gutter) {
      if (e.getArea() == EditorMouseEventArea.LINE_NUMBERS_AREA && EditorUtil.isBreakPointsOnLineNumbers()) {
        int line = EditorUtil.yToLogicalLineNoCustomRenderers(editor, e.getMouseEvent().getY());
        Document document = editor.getDocument();
        if (DocumentUtil.isValidLine(line, document)) {
          XSourcePositionImpl position = XSourcePositionImpl.create(FileDocumentManager.getInstance().getFile(document), line);
          if (position != null) {
            if (myLastPosition == null || !myLastPosition.getFile().equals(position.getFile()) || myLastPosition.getLine() != line) {
              // drop an icon first and schedule the available types calculation
              clear(gutter);
              myLastPosition = position;
              lineChangeHandler.lineChanged(editor, position);
            }
            return;
          }
        }
      }
      if (myLastIcon != null) {
        clear(gutter);
        myLastPosition = null;
        lineChangeHandler.exitedGutter();
      }
    }
  }

  private void clear(EditorGutterComponentEx gutter) {
    updateActiveLineNumberIcon(gutter, null, null);
    myLastIcon = null;
  }

  private static void updateActiveLineNumberIcon(@NotNull EditorGutterComponentEx gutter, @Nullable Icon icon, @Nullable Integer line) {
    if (gutter.getClientProperty("editor.gutter.context.menu") != null) return;
    boolean requireRepaint = false;
    if (gutter.getClientProperty("line.number.hover.icon") != icon) {
      gutter.putClientProperty("line.number.hover.icon", icon);
      gutter.putClientProperty("line.number.hover.icon.context.menu", icon == null ? null
                                                                                   : ActionManager.getInstance()
                                                                        .getAction("XDebugger.Hover.Breakpoint.Context.Menu"));
      if (icon != null) {
        gutter.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); // Editor updates cursor on MouseMoved, set it explicitly
      }
      requireRepaint = true;
    }
    if (!Objects.equals(gutter.getClientProperty("active.line.number"), line)) {
      gutter.putClientProperty("active.line.number", line);
      requireRepaint = true;
    }
    if (requireRepaint) {
      gutter.repaint();
    }
  }
}
