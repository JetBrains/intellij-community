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
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class VisualLinesIterator {
  private final Document myDocument;
  private final FoldRegion[] myFoldRegions;
  private final List<? extends SoftWrap> mySoftWraps;
  
  private int myVisualLine;
  private int myOffset;
  private int myLogicalLine = 1;
  private int myFoldRegion;
  private int mySoftWrap;
  
  VisualLinesIterator(@NotNull EditorView view, int startVisualLine) {
    EditorImpl editor = view.getEditor();
    SoftWrapModelImpl softWrapModel = editor.getSoftWrapModel();
    myDocument = editor.getDocument();
    FoldRegion[] regions = editor.getFoldingModel().fetchTopLevel();
    myFoldRegions = regions == null ? FoldRegion.EMPTY_ARRAY : regions;
    mySoftWraps = softWrapModel.getRegisteredSoftWraps();

    if (startVisualLine < 0 || startVisualLine >= editor.getVisibleLineCount()) {
      myOffset = -1;
    }
    else if (startVisualLine > 0) {
      myVisualLine = startVisualLine;
      myOffset = startVisualLine >= 0 && startVisualLine < editor.getVisibleLineCount() ? view.visualLineToOffset(startVisualLine) : -1;
      myLogicalLine = myDocument.getLineNumber(myOffset) + 1;
      mySoftWrap = softWrapModel.getSoftWrapIndex(myOffset) + 1;
      if (mySoftWrap <= 0) {
        mySoftWrap = -mySoftWrap;
      }
      myFoldRegion = editor.getFoldingModel().getLastCollapsedRegionBefore(myOffset) + 1;
    }
  }

  boolean atEnd() {
    return myOffset == -1;
  }
  
  void advance() {
    checkEnd();
    int nextWrapOffset = getNextSoftWrapOffset();
    myOffset = getNextVisualLineStartOffset(nextWrapOffset);
    if (myOffset == Integer.MAX_VALUE) {
      myOffset = -1;
    }
    else if (myOffset == nextWrapOffset) {
      mySoftWrap++;
    }
    myVisualLine++;
    while (myFoldRegion < myFoldRegions.length && myFoldRegions[myFoldRegion].getStartOffset() < myOffset) myFoldRegion++;
  }

  private int getNextSoftWrapOffset() {
    return mySoftWrap < mySoftWraps.size() ? mySoftWraps.get(mySoftWrap).getStart() : Integer.MAX_VALUE;
  }

  private int getNextVisualLineStartOffset(int nextWrapOffset) {
    while (myLogicalLine < myDocument.getLineCount()) {
      int lineStartOffset = myDocument.getLineStartOffset(myLogicalLine);
      if (lineStartOffset > nextWrapOffset) return nextWrapOffset;
      myLogicalLine++;
      if (!isCollapsed(lineStartOffset)) return lineStartOffset;
    }
    return nextWrapOffset;
  }
  
  private boolean isCollapsed(int offset) {
    while (myFoldRegion < myFoldRegions.length) {
      FoldRegion foldRegion = myFoldRegions[myFoldRegion];
      if (offset <= foldRegion.getStartOffset()) return false;
      if (offset <= foldRegion.getEndOffset()) return true;
      myFoldRegion++;
    }
    return false;
  }

  int getVisualLine() {
    checkEnd();
    return myVisualLine;
  }

  int getVisualLineStartOffset() {
    checkEnd();
    return myOffset;
  }
  
  int getStartLogicalLine() {
    checkEnd();
    return myLogicalLine - 1;
  }  
  
  int getStartOrPrevWrapIndex() {
    checkEnd();
    return mySoftWrap - 1;
  }
  
  int getStartFoldingIndex() {
    checkEnd();
    return myFoldRegion;
  }

  private void checkEnd() {
    if (atEnd()) throw new IllegalStateException("Iteration finished");
  }
  
}
