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
public class PersistentLineMarker extends RangeMarkerImpl {
  private int myLine;
  public PersistentLineMarker(DocumentEx document, int offset) {
    super(document, document.getLineStartOffset(document.getLineNumber(offset)), document.getLineEndOffset(document.getLineNumber(offset)));
    myLine = document.getLineNumber(offset);
  }

  @Override
  protected void changedUpdateImpl(DocumentEvent e) {
    DocumentEventImpl event = (DocumentEventImpl)e;
    if (event.isWholeTextReplaced()) {
      myLine = event.translateLineViaDiff(myLine);
      if (myLine < 0 || myLine >= getDocument().getLineCount()) {
        invalidate();
      }
      else {
        DocumentEx document = getDocument();
        myStart = document.getLineStartOffset(myLine);
        myEnd = document.getLineEndOffset(myLine);
      }
    }
    else {
      super.changedUpdateImpl(e);
      if (isValid()) {
        myLine = getDocument().getLineNumber(myStart);
      }
    }
  }

  @Override
  public String toString() {
    return "PersistentLineMarker" +
           (isGreedyToLeft() ? "[" : "(") +
           (isValid() ? "valid" : "invalid") + "," + getStartOffset() + "," + getEndOffset() + " - " + myLine +
           (isGreedyToRight() ? "]" : ")");
  }
}
