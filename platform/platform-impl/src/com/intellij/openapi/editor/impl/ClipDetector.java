// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.view.EditorView;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Allows performing clipping checks for painting in the editor.
 * Using this class will be faster than direct calculations if a lot of checks need to be performed in one painting sessions, and
 * requests are mostly grouped by visual lines, as caching of intermediate data is performed.
 */
@ApiStatus.Internal
public final class ClipDetector {
  private final EditorView myView;
  private final Document myDocument;
  private final FoldingModel myFoldingModel;
  private final Rectangle myClipRectangle;
  private final boolean myDisabled;

  private int myVisualLineStartOffset = -1;
  private int myVisualLineEndOffset = -1;
  private int myVisualLineClipStartOffset;
  private int myVisualLineClipEndOffset;

  /**
   * heuristics: if the content is not too wide, there's no need to spend time on clip checking:
   * painting all invisible elements cannot take too much time in that case
   */
  public static boolean isDisabled(@NotNull EditorImpl editor) {
    return editor.getContentComponent().getWidth() < 10 * editor.getScrollingModel().getVisibleArea().width;
  }

  public ClipDetector(
    EditorView view,
    Document document,
    FoldingModel foldingModel,
    Rectangle clipRectangle,
    boolean isDisabled
  ) {
    myView = view;
    myDocument = document;
    myFoldingModel = foldingModel;
    myClipRectangle = clipRectangle;
    myDisabled = isDisabled;
  }

  public boolean rangeCanBeVisible(int startOffset, int endOffset) {
    assert startOffset >= 0;
    assert startOffset <= endOffset;
    assert endOffset <= myDocument.getTextLength();
    if (myDisabled) return true;
    if (startOffset < myVisualLineStartOffset || startOffset > myVisualLineEndOffset) {
      myVisualLineStartOffset = EditorUtil.getNotFoldedLineStartOffset(myDocument, myFoldingModel, startOffset, false);
      myVisualLineEndOffset = EditorUtil.getNotFoldedLineEndOffset(myDocument, myFoldingModel, startOffset, false);
      int visualLine = myView.offsetToVisualLine(startOffset, false);
      int y = myView.visualLineToY(visualLine);
      myVisualLineClipStartOffset = myView.logicalPositionToOffset(myView.xyToLogicalPosition(new Point(myClipRectangle.x, y)));
      myVisualLineClipEndOffset = myView.logicalPositionToOffset(myView.xyToLogicalPosition(new Point(myClipRectangle.x + myClipRectangle.width, y)));
    }
    return endOffset > myVisualLineEndOffset || startOffset <= myVisualLineClipEndOffset && endOffset >= myVisualLineClipStartOffset;
  }
}
