// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.util.EditorUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Allows performing clipping checks for painting in the editor.
 * Using this class will be faster than direct calculations if a lot of checks need to be performed in one painting sessions, and
 * requests are mostly grouped by visual lines, as caching of intermediate data is performed.
 */
public final class ClipDetector {
  private final EditorImpl myEditor;
  private final Rectangle myClipRectangle;
  private final boolean myDisabled;
  
  private int myVisualLineStartOffset = -1;
  private int myVisualLineEndOffset = -1;
  private int myVisualLineClipStartOffset;
  private int myVisualLineClipEndOffset;

  public ClipDetector(@NotNull EditorImpl editor, Rectangle clipRectangle) {
    myEditor = editor;
    myClipRectangle = clipRectangle;
    // heuristics: if the content is not too wide, there's no need to spend time on clip checking:
    // painting all invisible elements cannot take too much time in that case
    myDisabled = editor.getContentComponent().getWidth() < 10 * editor.getScrollingModel().getVisibleArea().width;
  }

  public boolean rangeCanBeVisible(int startOffset, int endOffset) {
    assert startOffset >= 0;
    assert startOffset <= endOffset;
    assert endOffset <= myEditor.getDocument().getTextLength();
    if (myDisabled) return true;
    if (startOffset < myVisualLineStartOffset || startOffset > myVisualLineEndOffset) {
      myVisualLineStartOffset = EditorUtil.getNotFoldedLineStartOffset(myEditor, startOffset);
      myVisualLineEndOffset = EditorUtil.getNotFoldedLineEndOffset(myEditor, startOffset);
      int visualLine = myEditor.offsetToVisualLine(startOffset);
      int y = myEditor.visualLineToY(visualLine);
      myVisualLineClipStartOffset = myEditor.logicalPositionToOffset(myEditor.xyToLogicalPosition(new Point(myClipRectangle.x, y)));
      myVisualLineClipEndOffset = myEditor.logicalPositionToOffset(myEditor.xyToLogicalPosition(new Point(myClipRectangle.x +
                                                                                                          myClipRectangle.width, y)));
    }
    return endOffset > myVisualLineEndOffset || startOffset <= myVisualLineClipEndOffset && endOffset >= myVisualLineClipStartOffset;
  }
}
