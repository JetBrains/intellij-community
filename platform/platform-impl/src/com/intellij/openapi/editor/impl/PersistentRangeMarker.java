/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;

/**
 * @author max
 */
public class PersistentRangeMarker extends RangeMarkerImpl {
  private int myStartLine;
  private int myStartColumn;
  private int myEndLine;
  private int myEndColumn;

  public PersistentRangeMarker(DocumentEx document, int startOffset, int endOffset) {
    super(document, startOffset, endOffset);
    storeLinesAndCols();
  }

  private void storeLinesAndCols() {
    // document might have been changed already
    if (getStartOffset() < myDocument.getTextLength()) {
      myStartLine = myDocument.getLineNumber(getStartOffset());
      myStartColumn = getStartOffset() - myDocument.getLineStartOffset(myStartLine);
    }
    if (getEndOffset() < myDocument.getTextLength()) {
      myEndLine = myDocument.getLineNumber(getEndOffset());
      myEndColumn = getEndOffset() - myDocument.getLineStartOffset(myEndLine);
    }
  }

  @Override
  protected void changedUpdateImpl(DocumentEvent e) {
    DocumentEventImpl event = (DocumentEventImpl)e;
    if (event.isWholeTextReplaced()){
      myStartLine = event.translateLineViaDiffStrict(myStartLine);
      if (myStartLine < 0 || myStartLine >= getDocument().getLineCount()){
        invalidate();
      }
      else{
        myStart = getDocument().getLineStartOffset(myStartLine) + myStartColumn;
      }

      myEndLine = event.translateLineViaDiffStrict(myEndLine);
      if (myEndLine < 0 || myEndLine >= getDocument().getLineCount()){
        invalidate();
      }
      else{
        myEnd = getDocument().getLineStartOffset(myEndLine) + myEndColumn;
      }
      if (myEnd < myStart) {
        invalidate();
      }
    }
    else{
      super.changedUpdateImpl(e);
      if (isValid()){
        storeLinesAndCols();
      }
    }
  }
}
