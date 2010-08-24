/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;

/**
 * Adapter class for {@link DocumentListener} interface that allows to represent document change events
 * in terms of logical lines affected by it.
 *
 * @author Denis Zhdanov
 * @since Jul 7, 2010 4:24:52 PM
 */
public abstract class LineOrientedDocumentChangeAdapter implements PrioritizedDocumentListener {

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    Document document = event.getDocument();
    int startLine = document.getLineNumber(normalize(event.getDocument(), event.getOffset()));
    int endLine = document.getLineNumber(normalize(event.getDocument(), event.getOffset() + event.getOldLength()));
    int symbolsDifference = event.getNewLength() - event.getOldLength();
    beforeDocumentChange(startLine, endLine, symbolsDifference);
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    Document document = event.getDocument();
    int startLine = document.getLineNumber(normalize(event.getDocument(), event.getOffset()));
    int endLine = document.getLineNumber(normalize(event.getDocument(), event.getOffset() + event.getNewLength()));
    int symbolsDifference = event.getNewLength() - event.getOldLength();
    afterDocumentChange(startLine, endLine, symbolsDifference);
  }

  @Override
  public int getPriority() {
    return Integer.MAX_VALUE;
  }

  /**
   * Callback adapter method for {@link DocumentListener#beforeDocumentChange(DocumentEvent)} event.
   *
   * @param startLine           first logical document line affected by the target event (inclusive)
   * @param endLine             old last logical document line affected by the target event (inclusive)
   * @param symbolsDifference   difference in number in symbols applied to the target document
   */
  public abstract void beforeDocumentChange(int startLine, int endLine, int symbolsDifference);

  /**
   * Callback adapter method for {@link DocumentListener#documentChanged(DocumentEvent)} event.
   *
   * @param startLine           first logical document line affected by the target event (inclusive)
   * @param endLine             new last logical document line affected by the target event (inclusive)
   * @param symbolsDifference   difference in number in symbols applied to the target document
   */
  public abstract void afterDocumentChange(int startLine, int endLine, int symbolsDifference);

  private static int normalize(Document document, int offset) {
    if (offset < 0) {
      return 0;
    }

    if (offset >= document.getTextLength()) {
      return Math.max(document.getTextLength() - 1, 0);
    }
    return offset;
  }
}
