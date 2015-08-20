/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.util.EditorUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Allows to perform clipping checks for painting in editor. 
 * Using this class will be faster than direct calculations, if a lot of checks need to be performed in one painting sessions, and
 * requests are mostly grouped by visual lines, as caching of intermediate data is performed.
 */
public class ClipDetector {
  private final EditorImpl myEditor;
  private final Rectangle myClipRectangle;
  
  private int myVisualLineStartOffset = -1;
  private int myVisualLineEndOffset = -1;
  private int myVisualLineClipStartOffset;
  private int myVisualLineClipEndOffset;

  public ClipDetector(@NotNull EditorImpl editor, Rectangle clipRectangle) {
    myEditor = editor;
    myClipRectangle = clipRectangle;
  }

  public boolean rangeCanBeVisible(int startOffset, int endOffset) {
    assert startOffset >= 0;
    assert startOffset <= endOffset;
    assert endOffset <= myEditor.getDocument().getTextLength();
    if (myEditor.getSettings().isUseSoftWraps()) return true; 
    if (startOffset < myVisualLineStartOffset || startOffset > myVisualLineEndOffset) {
      myVisualLineStartOffset = EditorUtil.getNotFoldedLineStartOffset(myEditor, startOffset);
      myVisualLineEndOffset = EditorUtil.getNotFoldedLineEndOffset(myEditor, startOffset);
      int visualLine = myEditor.offsetToVisualLine(startOffset);
      int y = myEditor.visibleLineToY(visualLine);
      myVisualLineClipStartOffset = myEditor.logicalPositionToOffset(myEditor.xyToLogicalPosition(new Point(myClipRectangle.x, y)));
      myVisualLineClipEndOffset = myEditor.logicalPositionToOffset(myEditor.xyToLogicalPosition(new Point(myClipRectangle.x +
                                                                                                          myClipRectangle.width, y)));
    }
    return endOffset > myVisualLineEndOffset || startOffset <= myVisualLineClipEndOffset && endOffset >= myVisualLineClipStartOffset;
  }
}
