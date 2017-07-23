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
