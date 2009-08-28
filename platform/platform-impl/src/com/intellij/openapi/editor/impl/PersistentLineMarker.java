package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;

/**
 * @author max
 */
public class PersistentLineMarker extends RangeMarkerImpl {
  private int myLine;
  public PersistentLineMarker(Document document, int offset) {
    super(document, offset, offset);
    myLine = document.getLineNumber(offset);
  }

  public void documentChanged(DocumentEvent e) {
    if (!isValid()) return;

    DocumentEventImpl event = (DocumentEventImpl) e;
    if (event.isWholeTextReplaced()) {
      myLine = event.translateLineViaDiff(myLine);
      if (myLine < 0 || myLine >= getDocument().getLineCount()) {
        invalidate();
      } else {
        myStart = MarkupModelImpl.getFirstNonspaceCharOffset(getDocument(), myLine);
        myEnd = myStart;
      }
    } else {
      super.documentChanged(e);
      if (isValid()) {
        myLine = getDocument().getLineNumber(myStart);
      }
    }
  }
}
