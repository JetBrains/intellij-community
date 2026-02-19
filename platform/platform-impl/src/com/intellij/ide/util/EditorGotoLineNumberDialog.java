// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.impl.RelativeLineHelper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.PatternUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApiStatus.Internal
public final class EditorGotoLineNumberDialog extends GotoLineNumberDialog {
  private final Editor myEditor;
  private static final Pattern relativeNumberPattern = PatternUtil.compileSafe("\\s*([+-]\\d+)\\s*", null);

  public EditorGotoLineNumberDialog(Project project, Editor editor) {
    super(project);
    myEditor = editor;
    init();
  }

  @Override
  protected Coordinates getCoordinates() {
    Coordinates c = super.getCoordinates();
    if (c != null) return c;

    Matcher relativeMatcher = relativeNumberPattern.matcher(getText());
    if (relativeMatcher.matches()) {
      int caretLine = myEditor.getCaretModel().getLogicalPosition().line;
      int relativeLine = Integer.parseInt(relativeMatcher.group(1));
      int logicalLine = RelativeLineHelper.INSTANCE.getLogicalLine(myEditor, caretLine, relativeLine);

      int linesTotal = myEditor.getDocument().getLineCount();
      return new Coordinates(Math.max(0, Math.min(logicalLine, linesTotal - 1)), 0);
    }
    return null;
  }

  @Override
  protected void doOKAction() {
    Coordinates coordinates = getCoordinates();
    if (coordinates == null) return;

    LogicalPosition position = new LogicalPosition(coordinates.row(), coordinates.column());
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
    LogicalPosition position = new LogicalPosition(coordinates.row(), coordinates.column());
    return myEditor.logicalPositionToOffset(position);
  }

  @Override
  protected @NotNull Coordinates offsetToCoordinates(int offset) {
    LogicalPosition position = myEditor.offsetToLogicalPosition(offset);
    return new Coordinates(position.line, position.column);
  }
}
