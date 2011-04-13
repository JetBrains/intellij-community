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
 * This class is an extension to range marker that tries to restore its range even in situations when target text referenced by it
 * is replaced.
 * <p/>
 * Example: consider that the user selects all text at editor (Ctrl+A), copies it to the buffer (Ctrl+C) and performs paste (Ctrl+V).
 * All document text is replaced then but in essence it's the same, hence, we may want particular range markers to be still valid.   
 * 
 * @author max
 */
class PersistentRangeMarker extends RangeMarkerImpl {
  private int myStartLine;
  private int myStartColumn;
  private int myEndLine;
  private int myEndColumn;

  PersistentRangeMarker(DocumentEx document, int startOffset, int endOffset, boolean register) {
    super(document, startOffset, endOffset, register);
    storeLinesAndCols(null);
  }

  private void storeLinesAndCols(DocumentEvent e) {
    // document might have been changed already
    if (getStartOffset() < myDocument.getTextLength()) {
      myStartLine = myDocument.getLineNumber(getStartOffset());
      myStartColumn = getStartOffset() - myDocument.getLineStartOffset(myStartLine);
      if (myStartColumn < 0) {
        invalidate(e);
      }
    }
    if (getEndOffset() < myDocument.getTextLength()) {
      myEndLine = myDocument.getLineNumber(getEndOffset());
      myEndColumn = getEndOffset() - myDocument.getLineStartOffset(myEndLine);
      if (myEndColumn < 0) {
        invalidate(e);
      }
    }
  }

  @Override
  protected void changedUpdateImpl(DocumentEvent e) {
    DocumentEventImpl event = (DocumentEventImpl)e;
    if (PersistentRangeMarkerUtil.shouldTranslateViaDiff(event, this)){
      myStartLine = event.translateLineViaDiffStrict(myStartLine);
      if (myStartLine < 0 || myStartLine >= getDocument().getLineCount()){
        invalidate(e);
      }
      else{
        setIntervalStart(getDocument().getLineStartOffset(myStartLine) + myStartColumn);
      }

      myEndLine = event.translateLineViaDiffStrict(myEndLine);
      if (myEndLine < 0 || myEndLine >= getDocument().getLineCount()){
        invalidate(e);
      }
      else{
        setIntervalEnd(getDocument().getLineStartOffset(myEndLine) + myEndColumn);
      }
    }
    else {
      super.changedUpdateImpl(e);
      if (isValid()){
        storeLinesAndCols(e);
      }
    }
    if (getEndOffset() < getStartOffset() || getEndOffset() > getDocument().getTextLength()) {
      invalidate(e);
    }
  }

  @Override
  public String toString() {
    return "PersistentRangeMarker" +
           (isGreedyToLeft() ? "[" : "(") +
           (isValid() ? "valid" : "invalid") + "," + getStartOffset() + "," + getEndOffset() +
           " " + myStartLine + ":" + myStartColumn + "-" + myEndLine + ":" + myEndColumn +
           (isGreedyToRight() ? "]" : ")");
  }
}
