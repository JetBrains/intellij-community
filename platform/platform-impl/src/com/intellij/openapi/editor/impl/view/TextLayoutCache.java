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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.impl.EditorDocumentPriorities;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Editor text layout storage. Layout is stored on a per-logical-line basis, 
 * it's created lazily (when requested) and invalidated on document changes or when explicitly requested.
 * 
 * @see LineLayout
 */
class TextLayoutCache implements PrioritizedDocumentListener, Disposable {
  private final EditorView myView;
  private final Document myDocument;
  private final LineLayout myBidiNotRequiredMarker;
  private ArrayList<LineLayout> myLines = new ArrayList<LineLayout>();
  private int myDocumentChangeOldEndLine;

  TextLayoutCache(EditorView view) {
    myView = view;
    myDocument = view.getEditor().getDocument();
    myDocument.addDocumentListener(this, this);
    myBidiNotRequiredMarker = new LineLayout(view, "", Font.PLAIN);
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.EDITOR_TEXT_LAYOUT_CACHE;
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    myDocumentChangeOldEndLine = getAdjustedLineNumber(event.getOffset() + event.getOldLength());
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    int startLine = myDocument.getLineNumber(event.getOffset());
    int newEndLine = getAdjustedLineNumber(event.getOffset() + event.getNewLength());
    invalidateLines(startLine, myDocumentChangeOldEndLine, newEndLine, !LineLayout.isBidiLayoutRequired(event.getNewFragment()));
  }

  @Override
  public void dispose() {
    myLines = null;
  }

  private int getAdjustedLineNumber(int offset) {
    return myDocument.getTextLength() == 0 ? -1 : myDocument.getLineNumber(offset);
  }
  
  void resetToDocumentSize(boolean documentChangedWithoutNotification) {
    checkDisposed();
    invalidateLines(0, myLines.size() - 1, myDocument.getLineCount() - 1, !documentChangedWithoutNotification);
  }

  void invalidateLines(int startLine, int endLine) {
    invalidateLines(startLine, endLine, endLine, true);
  }
  
  private void invalidateLines(int startLine, int oldEndLine, int newEndLine, boolean keepBidiNotRequiredState) {
    checkDisposed();
    int endLine = Math.min(oldEndLine, newEndLine);
    for (int line = startLine; line <= endLine; line++) {
      LineLayout lineLayout = myLines.get(line);
      if (lineLayout != null) {
        myLines.set(line, keepBidiNotRequiredState && lineLayout.isLtr() ? myBidiNotRequiredMarker : null);
      }
    }
    if (oldEndLine < newEndLine) {
      myLines.addAll(oldEndLine + 1, Collections.nCopies(newEndLine - oldEndLine, (LineLayout)null));
    } else if (oldEndLine > newEndLine) {
      myLines.subList(newEndLine + 1, oldEndLine + 1).clear();
    }
  }

  @NotNull
  LineLayout getLineLayout(int line) {
    checkDisposed();
    LineLayout result = myLines.get(line);
    if (result == null || result == myBidiNotRequiredMarker) {
      result = createLineLayout(line, result == myBidiNotRequiredMarker);
      myLines.set(line, result);
    }
    return result;
  }

  @NotNull
  private LineLayout createLineLayout(int line, boolean skipBidiLayout) {
    int lineStart = myDocument.getLineStartOffset(line);
    int lineEnd = myDocument.getLineEndOffset(line);
    return new LineLayout(myView, lineStart, lineEnd, skipBidiLayout);
  }

  private void checkDisposed() {
    if (myLines == null) throw new IllegalStateException("Editor is already disposed");
  }
}
