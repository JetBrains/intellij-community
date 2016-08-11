/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.impl.EditorDocumentPriorities;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Caches information allowing faster offset<->logicalPosition conversions even for long lines.
 * Requests for conversion can be made from under read action, document changes and cache invalidation should be done in EDT.
 */
@SuppressWarnings("SynchronizeOnThis")
class LogicalPositionCache implements PrioritizedDocumentListener, Disposable, Dumpable {
  private final Document myDocument;
  private final EditorView myView;
  private ArrayList<LineData> myLines = new ArrayList<>();
  private int myTabSize = -1;
  private int myDocumentChangeOldEndLine;

  LogicalPositionCache(EditorView view) {
    myView = view;
    myDocument = view.getEditor().getDocument();
    myDocument.addDocumentListener(this, this);
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.LOGICAL_POSITION_CACHE;
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    myDocumentChangeOldEndLine = getAdjustedLineNumber(event.getOffset() + event.getOldLength());
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    int startLine = myDocument.getLineNumber(event.getOffset());
    int newEndLine = getAdjustedLineNumber(event.getOffset() + event.getNewLength());
    invalidateLines(startLine, myDocumentChangeOldEndLine, newEndLine, CharArrayUtil.indexOf(event.getNewFragment(), "\t", 0) == -1);
  }

  synchronized void reset(boolean force) {
    checkDisposed();
    int oldTabSize = myTabSize;
    myTabSize = myView.getTabSize();
    if (force || oldTabSize != myTabSize) {
      invalidateLines(0, myLines.size() - 1, myDocument.getLineCount() - 1, !force && myLines.size() == myDocument.getLineCount());
    }
  }

  @NotNull
  synchronized LogicalPosition offsetToLogicalPosition(int offset) {
    int textLength = myDocument.getTextLength();
    if (offset <= 0 || textLength == 0) {
      return new LogicalPosition(0, 0);
    }
    offset = Math.min(offset, textLength);
    int line = myDocument.getLineNumber(offset);
    LineData lineData = getLineInfo(line);
    return new LogicalPosition(line, lineData.offsetToLogicalColumn(myDocument, line, myTabSize, offset));
  }
  
  synchronized int offsetToLogicalColumn(int line, int intraLineOffset) {
    if (line < 0 || line >= myDocument.getLineCount()) return 0;
    LineData lineData = getLineInfo(line);
    return lineData.offsetToLogicalColumn(myDocument, line, myTabSize, myDocument.getLineStartOffset(line) + intraLineOffset);
  }

  synchronized int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    int line = pos.line;
    if (line >= myDocument.getLineCount()) return myDocument.getTextLength();
    LineData lineData = getLineInfo(line);
    return lineData.logicalColumnToOffset(myDocument, line, myTabSize, pos.column);
  }

  private int getAdjustedLineNumber(int offset) {
    return myDocument.getTextLength() == 0 ? -1 : myDocument.getLineNumber(offset);
  }

  private synchronized void invalidateLines(int startLine, int oldEndLine, int newEndLine, boolean preserveTrivialLines) {
    checkDisposed();
    if (preserveTrivialLines) {
      for (int line = startLine; line <= oldEndLine; line++) {
        LineData data = myLines.get(line);
        if (data == null || data.columnCache != null) {
          preserveTrivialLines = false;
          break;
        }
      }
    }
    if (!preserveTrivialLines) {
      int endLine = Math.min(oldEndLine, newEndLine);
      for (int line = startLine; line <= endLine; line++) {
        myLines.set(line, null);
      }
    }
    if (oldEndLine < newEndLine) {
      myLines.addAll(oldEndLine + 1, Collections.nCopies(newEndLine - oldEndLine, preserveTrivialLines ? LineData.TRIVIAL : null));
    } else if (oldEndLine > newEndLine) {
      myLines.subList(newEndLine + 1, oldEndLine + 1).clear();
    }
  }

  @NotNull
  private LineData getLineInfo(int line) {
    checkDisposed();
    LineData result = myLines.get(line);
    if (result == null) {
      result = LineData.create(myDocument, line, myTabSize);
      myLines.set(line, result);
    }
    return result;
  }

  @Override
  public synchronized void dispose() {
    myLines = null;
  }

  private void checkDisposed() {
    if (myLines == null) myView.getEditor().throwDisposalError("Editor is already disposed");
  }
  
  synchronized void validateState() {
    int lineCount = myDocument.getLineCount();
    int cacheSize = myLines.size();
    if (cacheSize != lineCount) throw new IllegalStateException("Line count: " + lineCount + ", cache size: " + cacheSize);
    int tabSize = myView.getTabSize();
    for (int i = 0; i < cacheSize; i++) {
      LineData data = myLines.get(i);
      if (data != null) {
        LineData actual = LineData.create(myDocument, i, tabSize);
        if (!Arrays.equals(data.columnCache, actual.columnCache)) throw new IllegalStateException("Wrong cache state at line " + i);
      }
    }
  }

  @NotNull
  @Override
  public String dumpState() {
    try {
      validateState();
      return "valid";
    }
    catch (Exception e) {
      return "invalid (" + e.getMessage() + ")";
    }
  }

  private static class LineData {    
    private static final LineData TRIVIAL = new LineData(null);
    private static final int CACHE_FREQUENCY = 1024; // logical column will be cached for each CACHE_FREQUENCY-th character on the line
    
    private final int[] columnCache;
    
    private LineData(int[] columnData) {
      columnCache = columnData;
    }
    
    private static LineData create(@NotNull Document document, int line, int tabSize) {
      int start = document.getLineStartOffset(line);
      int end = document.getLineEndOffset(line);
      int cacheSize = (end - start) / CACHE_FREQUENCY;
      int[] cache = new int[cacheSize];
      CharSequence text = document.getImmutableCharSequence();
      int column = 0;
      boolean hasTabs = false;
      for (int i = start; i < end; i++) {
        if (i > start && (i - start) % CACHE_FREQUENCY == 0) {
          cache[(i - start) / CACHE_FREQUENCY - 1] = column;
        }
        if (text.charAt(i) == '\t') {
          column = (column / tabSize + 1) * tabSize;
          hasTabs = true;
        }
        else {
          column++;
        }
      }
      return hasTabs ? new LineData(cache) : TRIVIAL;
    }

    private int offsetToLogicalColumn(@NotNull Document document, int line, int tabSize, int offset) {
      offset = Math.min(offset, document.getLineEndOffset(line));
      int lineStartOffset = document.getLineStartOffset(line);
      int relOffset = offset - lineStartOffset;
      if (columnCache == null) return relOffset;
      int cacheIndex = relOffset / CACHE_FREQUENCY;
      int startOffset = lineStartOffset + cacheIndex * CACHE_FREQUENCY;
      int column = cacheIndex == 0 ? 0 : columnCache[cacheIndex - 1];
      CharSequence text = document.getImmutableCharSequence();
      for (int i = startOffset; i < offset; i++) {
        if (text.charAt(i) == '\t') {
          column = (column / tabSize + 1) * tabSize;
        }
        else {
          column++;
        }
      }
      return column;
    }
    
    private int logicalColumnToOffset(@NotNull Document document, int line, int tabSize, int logicalColumn) {
      int lineStartOffset = document.getLineStartOffset(line);
      int lineEndOffset = document.getLineEndOffset(line);
      if (columnCache == null) {
        int result = lineStartOffset + logicalColumn;
        return result < 0 || // guarding over overflow
               result > lineEndOffset ? lineEndOffset : result;
      }
      int pos = Arrays.binarySearch(columnCache, logicalColumn);
      if (pos >= 0) return lineStartOffset + (pos + 1) * CACHE_FREQUENCY;
      int startOffset = lineStartOffset + (- pos - 1) * CACHE_FREQUENCY;
      int column = pos == -1 ? 0 : columnCache[- pos - 2];
      CharSequence text = document.getImmutableCharSequence();
      for (int i = startOffset; i < lineEndOffset; i++) {
        if (text.charAt(i) == '\t') {
          column = (column / tabSize + 1) * tabSize;
        }
        else {
          column++;
        }
        if (logicalColumn < column) return i;
      }
      return lineEndOffset;
    }
  }
}
