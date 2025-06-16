// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.impl.EditorDocumentPriorities;
import com.intellij.util.ArrayUtil;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Caches information allowing faster offset<->logicalPosition conversions even for long lines.
 * Requests for conversion can be made from under read action, document changes and cache invalidation should be done in EDT.
 */
@ApiStatus.Internal
public final class LogicalPositionCache implements PrioritizedDocumentListener, Disposable, Dumpable {
  private final Document myDocument;
  private final EditorView myView;
  private ArrayList<LineData> myLines = new ArrayList<>();
  private int myTabSize = -1;
  private int myDocumentChangeOldEndLine;
  // application's read-write lock should guarantee that writes to this field (happening under write action)
  // will be visible for reads (happening under read action)
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private boolean myUpdateInProgress;
  private long myDocumentStamp = Long.MIN_VALUE;

  LogicalPositionCache(EditorView view) {
    myView = view;
    myDocument = view.getDocument();
    myDocument.addDocumentListener(this, this);
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.LOGICAL_POSITION_CACHE;
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent event) {
    assert !myView.isAd();
    myUpdateInProgress = true;
    myDocumentChangeOldEndLine = getAdjustedLineNumber(event.getOffset() + event.getOldLength());
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent event) {
    assert !myView.isAd();
    try {
      int startLine = myDocument.getLineNumber(event.getOffset());
      int newEndLine = getAdjustedLineNumber(event.getOffset() + event.getNewLength());
      invalidateLines(startLine, myDocumentChangeOldEndLine, newEndLine, isSimpleText(event.getNewFragment()));
    }
    finally {
      myUpdateInProgress = false;
    }
  }

  // text for which offset<->logicalColumn conversion is trivial
  private static boolean isSimpleText(@NotNull CharSequence text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\t' || c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE) return false;
    }
    return true;
  }

  synchronized void reset(boolean force) {
    checkDisposed();
    int oldTabSize = myTabSize;
    myTabSize = myView.getTabSize();
    if (force || oldTabSize != myTabSize) {
      invalidateLines(0, myLines.size() - 1, myDocument.getLineCount() - 1, !force && myLines.size() == myDocument.getLineCount());
    }
  }

  synchronized @NotNull LogicalPosition offsetToLogicalPosition(int offset) {
    resetIfOutdated();
    if (myUpdateInProgress) throw new IllegalStateException();
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
    resetIfOutdated();
    if (myUpdateInProgress) throw new IllegalStateException();
    if (line < 0 || line >= myDocument.getLineCount()) return 0;
    LineData lineData = getLineInfo(line);
    return lineData.offsetToLogicalColumn(myDocument, line, myTabSize, myDocument.getLineStartOffset(line) + intraLineOffset);
  }

  synchronized int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    resetIfOutdated();
    int line = pos.line;
    int column = pos.column;
    if (line >= myDocument.getLineCount()) return myDocument.getTextLength();
    if (myUpdateInProgress) {
      // direct calculation when we cannot use cache
      // (use case - com.intellij.openapi.editor.impl.CaretImpl.PositionMarker.changedUpdateImpl())
      int lineStartOffset = myDocument.getLineStartOffset(line);
      int lineEndOffset = myDocument.getLineEndOffset(line);
      return calcOffset(myDocument.getImmutableCharSequence(), column, 0, lineStartOffset, lineEndOffset, myTabSize);
    }
    LineData lineData = getLineInfo(line);
    return lineData.logicalColumnToOffset(myDocument, line, myTabSize, column);
  }

  static int calcOffset(@NotNull CharSequence text, int column, int startColumn, int startOffset, int endOffset, int tabSize) {
    int currentColumn = startColumn;
    for (int i = startOffset; i < endOffset; i++) {
      char c = text.charAt(i);
      if (c == '\t') {
        currentColumn = (currentColumn / tabSize + 1) * tabSize;
      }
      else if (i + 1 < text.length() && Character.isHighSurrogate(c) && Character.isLowSurrogate(text.charAt(i + 1))) {
        if (currentColumn == column) return i;
      }
      else {
        currentColumn++;
      }
      if (currentColumn > column) return i;
    }
    return endOffset;
  }

  static int calcColumn(@NotNull CharSequence text, int startOffset, int startColumn, int offset, int tabSize) {
    int column = startColumn;
    for (int i = startOffset; i < offset; i++) {
      char c = text.charAt(i);
      if (c == '\t') {
        column = (column / tabSize + 1) * tabSize;
      }
      else if (i + 1 >= text.length() || !Character.isHighSurrogate(c) || !Character.isLowSurrogate(text.charAt(i + 1))) {
        column++;
      }
    }
    return column;
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

  private @NotNull LineData getLineInfo(int line) {
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

  @VisibleForTesting
  public synchronized void validateState() {
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

  @Override
  public @NotNull String dumpState() {
    try {
      validateState();
      return "valid";
    }
    catch (Exception e) {
      return "invalid (" + e.getMessage() + ")";
    }
  }

  private void resetIfOutdated() {
    if (myView.isAd() && myDocumentStamp != myDocument.getModificationStamp()) {
      reset(true);
      myDocumentStamp = myDocument.getModificationStamp();
    }
  }

  private static final class LineData {
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
      int[] cache = ArrayUtil.newIntArray(cacheSize);
      CharSequence text = document.getImmutableCharSequence();
      int column = 0;
      boolean hasTabsOrSurrogates = false;
      for (int i = start; i < end; i++) {
        if (i > start && (i - start) % CACHE_FREQUENCY == 0) {
          cache[(i - start) / CACHE_FREQUENCY - 1] = column;
        }
        char c = text.charAt(i);
        if (c == '\t') {
          column = (column / tabSize + 1) * tabSize;
          hasTabsOrSurrogates = true;
        }
        else {
          if (Character.isHighSurrogate(c)) {
            hasTabsOrSurrogates = true;
            if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) continue;
          }
          else {
            hasTabsOrSurrogates |= Character.isLowSurrogate(c);
          }
          column++;
        }
      }
      if (cacheSize > 0 && (end - start) % CACHE_FREQUENCY == 0) cache[cacheSize - 1] = column;
      return hasTabsOrSurrogates ? new LineData(cache) : TRIVIAL;
    }

    private int offsetToLogicalColumn(@NotNull Document document, int line, int tabSize, int offset) {
      offset = Math.min(offset, document.getLineEndOffset(line));
      int lineStartOffset = document.getLineStartOffset(line);
      int relOffset = offset - lineStartOffset;
      if (columnCache == null) return relOffset;
      int cacheIndex = relOffset / CACHE_FREQUENCY;
      int startOffset = lineStartOffset + cacheIndex * CACHE_FREQUENCY;
      int startColumn = cacheIndex == 0 ? 0 : columnCache[cacheIndex - 1];
      return calcColumn(document.getImmutableCharSequence(), startOffset, startColumn, offset, tabSize);
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
      if (pos >= 0) {
        int result = lineStartOffset + (pos + 1) * CACHE_FREQUENCY;
        return DocumentUtil.isInsideSurrogatePair(document, result) ? result - 1 : result;
      }
      int startOffset = lineStartOffset + (- pos - 1) * CACHE_FREQUENCY;
      int column = pos == -1 ? 0 : columnCache[- pos - 2];
      return calcOffset(document.getImmutableCharSequence(), logicalColumn, column, startOffset, lineEndOffset, tabSize);
    }
  }
}
