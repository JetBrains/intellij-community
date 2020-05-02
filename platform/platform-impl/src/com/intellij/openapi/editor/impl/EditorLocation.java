// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

class EditorLocation {
  private final Editor myEditor;
  private final Point myPoint;
  private VisualPosition myVisualPosition;
  private LogicalPosition myLogicalPosition;
  private int myOffset = -1;
  private int myVisualLineBaseY = -1;

  EditorLocation(@NotNull Editor editor, @NotNull Point point) {
    myEditor = editor;
    myPoint = point;
  }

  @NotNull Point getPoint() {
    return myPoint;
  }

  @NotNull VisualPosition getVisualPosition() {
    if (myVisualPosition == null) {
      myVisualPosition = myEditor.xyToVisualPosition(myPoint);
    }
    return myVisualPosition;
  }

  int getVisualLineBaseY() {
    if (myVisualLineBaseY < 0) {
      myVisualLineBaseY = myEditor.visualLineToY(getVisualPosition().line);
    }
    return myVisualLineBaseY;
  }

  @NotNull LogicalPosition getLogicalPosition() {
    if (myLogicalPosition == null) {
      myLogicalPosition = myEditor.visualToLogicalPosition(getVisualPosition());
    }
    return myLogicalPosition;
  }

  int getOffset() {
    if (myOffset < 0) {
      myOffset = myEditor.logicalPositionToOffset(getLogicalPosition());
    }
    return myOffset;
  }
}
