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

/**
 * Adapter class for {@link DocumentListener} interface that allows to represent document change events
 * in terms of logical lines affected by it.
 *
 * @author Denis Zhdanov
 * @since Jul 7, 2010 4:24:52 PM
 */
public abstract class LineOrientedDocumentChangeAdapter implements DocumentListener {

  private enum ChangeType {
    BEFORE, AFTER
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    onChange(event, ChangeType.BEFORE);
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    onChange(event, ChangeType.AFTER);
  }

  /**
   * Callback adapter method for {@link DocumentListener#beforeDocumentChange(DocumentEvent)} event.
   *
   * @param startLine           first logical document line affected by the target event (inclusive)
   * @param endLine             last logical document line affected by the target event (inclusive)
   * @param symbolsDifference   difference in number in symbols applied to the target document
   */
  public abstract void beforeDocumentChange(int startLine, int endLine, int symbolsDifference);

  /**
   * Callback adapter method for {@link DocumentListener#documentChanged(DocumentEvent)} event.
   *
   * @param startLine           first logical document line affected by the target event (inclusive)
   * @param endLine             last logical document line affected by the target event (inclusive)
   * @param symbolsDifference   difference in number in symbols applied to the target document
   */
  public abstract void afterDocumentChange(int startLine, int endLine, int symbolsDifference);

  private void onChange(DocumentEvent event, ChangeType type) {
    Document document = event.getDocument();
    int startLine = document.getLineNumber(normalize(event.getDocument(), event.getOffset()));
    int endLine = document.getLineNumber(
      normalize(event.getDocument(), Math.max(event.getOffset() + event.getNewLength(), event.getOffset() + event.getOldLength()))
    );

    int symbolsDifference = event.getNewLength() - event.getOldLength();
    switch (type) {
      case AFTER: beforeDocumentChange(startLine, endLine, symbolsDifference); break;
      case BEFORE: afterDocumentChange(startLine, endLine, symbolsDifference); break;
      default: throw new IllegalStateException("Unsupported event change type: " + type);
    }
  }

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
