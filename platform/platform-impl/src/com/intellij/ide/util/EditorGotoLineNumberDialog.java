// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.NotNull;

public class EditorGotoLineNumberDialog extends GotoLineNumberDialog {
  private final Editor myEditor;

  public EditorGotoLineNumberDialog(Project project, Editor editor) {
    super(project);
    myEditor = editor;
    init();
  }

  @Override
  protected void doOKAction() {
    Coordinates coordinates = getCoordinates();
    if (coordinates == null) return;

    LogicalPosition position = new LogicalPosition(coordinates.row, coordinates.column);
    myEditor.getCaretModel().removeSecondaryCarets();
    myEditor.getCaretModel().moveToLogicalPosition(position);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    myEditor.getSelectionModel().removeSelection();
    IdeFocusManager.getGlobalInstance().requestFocus(myEditor.getContentComponent(), true);
    super.doOKAction();
  }

  @Override
  protected int getLine() {
    return myEditor.getCaretModel().getLogicalPosition().line;
  }

  @Override
  protected int getColumn() {
    return myEditor.getCaretModel().getLogicalPosition().column;
  }

  @Override
  protected int getOffset() {
    return myEditor.getCaretModel().getOffset();
  }

  @Override
  protected int getMaxOffset() {
    return myEditor.getDocument().getTextLength();
  }

  @Override
  protected int coordinatesToOffset(@NotNull Coordinates coordinates) {
    LogicalPosition position = new LogicalPosition(coordinates.row, coordinates.column);
    return myEditor.logicalPositionToOffset(position);
  }

  @NotNull
  @Override
  protected Coordinates offsetToCoordinates(int offset) {
    LogicalPosition position = myEditor.offsetToLogicalPosition(offset);
    return new Coordinates(position.line, position.column);
  }
}
