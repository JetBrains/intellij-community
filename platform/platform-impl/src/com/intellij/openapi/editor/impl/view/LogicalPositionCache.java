// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.diagnostic.Dumpable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentSnapshot;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.ex.ElfCandidate;
import com.intellij.openapi.editor.impl.EditorDocumentPriorities;
import com.intellij.util.DocumentInternalUtil;
import kotlinx.collections.immutable.ExtensionsKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Caches information allowing faster offset<->logicalPosition conversions even for long lines.
 * Requests for conversion can be made without read action, document changes and cache invalidation should be done in EDT.
 */
@ApiStatus.Internal
@ElfCandidate
public final class LogicalPositionCache implements PrioritizedDocumentListener, Disposable, Dumpable {
  private static final AtomicReferenceFieldUpdater<LogicalPositionCache, LogicalLines> SNAPSHOT_UPDATER =
    AtomicReferenceFieldUpdater.newUpdater(LogicalPositionCache.class, LogicalLines.class, "snapshot");

  private volatile LogicalLines snapshot;
  private final Runnable throwEditorDisposed;

  LogicalPositionCache(@NotNull Document document, @NotNull Runnable throwEditorDisposed) {
    this.snapshot = new LogicalLines(
      DocumentInternalUtil.getDocumentSnapshot(document),
      ExtensionsKt.persistentListOf(),
      -1
    );
    this.throwEditorDisposed = throwEditorDisposed;
    document.addDocumentListener(this, this);
  }

  @NotNull LogicalPosition offsetToLogicalPosition(int offset) {
    LogicalLines snapshot = getSnapshot();
    int textLength = snapshot.document.textLength();
    if (offset <= 0 || textLength == 0) {
      return new LogicalPosition(0, 0);
    }
    offset = Math.min(offset, textLength);
    int line = snapshot.document.lineNumber(offset);
    int column = getSnapshotWithLine(snapshot, line).offsetToLogicalColumn(line, offset);
    return new LogicalPosition(line, column);
  }

  int offsetToLogicalColumn(int line, int intraLineOffset) {
    LogicalLines snapshot = getSnapshot();
    if (line < 0 || line >= snapshot.document.lineCount()) {
      return 0;
    }
    int offset = snapshot.document.lineStartOffset(line) + intraLineOffset;
    return getSnapshotWithLine(snapshot, line).offsetToLogicalColumn(line, offset);
  }

  int logicalPositionToOffset(@NotNull LogicalPosition pos) {
    LogicalLines snapshot = getSnapshot();
    if (pos.line >= snapshot.document.lineCount()) {
      return snapshot.document.textLength();
    }
    return getSnapshotWithLine(snapshot, pos.line).logicalColumnToOffset(pos.line, pos.column);
  }

  void reset(boolean force, int newTabSize) {
    while (true) {
      LogicalLines snapshot = getSnapshot();
      int tabSize = snapshot.tabSize;
      if (!force && tabSize == newTabSize) {
        return;
      }
      LogicalLines newSnapshot = snapshot.withInvalidatedLines(newTabSize, force);
      if (SNAPSHOT_UPDATER.compareAndSet(this, snapshot, newSnapshot)) {
        return;
      }
    }
  }

  @Override
  public int getPriority() {
    return EditorDocumentPriorities.LOGICAL_POSITION_CACHE;
  }

  @Override
  public void documentChanged(@NotNull DocumentEvent event) {
    LogicalLines snapshot = getSnapshot();
    DocumentSnapshot oldDocument = snapshot.document;
    DocumentSnapshot newDocument = DocumentInternalUtil.getDocumentSnapshot(event.getDocument());
    int oldEndLine = getAdjustedLineNumber(oldDocument, event.getOffset() + event.getOldLength());
    int newEndLine = getAdjustedLineNumber(newDocument, event.getOffset() + event.getNewLength());
    int startLine = newDocument.lineNumber(event.getOffset());
    boolean preserveTrivialLines = isSimpleText(event.getNewFragment());
    do {
      LogicalLines newSnapshot = snapshot.withInvalidatedLines(newDocument, startLine, oldEndLine, newEndLine, preserveTrivialLines);
      if (SNAPSHOT_UPDATER.compareAndSet(this, snapshot, newSnapshot)) {
        return;
      }
      snapshot = getSnapshot();
      if (snapshot.document != oldDocument) {
        throw new IllegalStateException("Document changed while processing documentChanged event");
      }
    } while (true);
  }

  @Override
  public void dispose() {
    snapshot = null;
  }

  @Override
  public @NotNull String dumpState() {
    try {
      validateState();
      return "valid";
    } catch (Exception e) {
      return "invalid (" + e.getMessage() + ")";
    }
  }

  @VisibleForTesting
  public void validateState() {
    getSnapshot().validateState();
  }

  private LogicalLines getSnapshot() {
    LogicalLines snapshot = this.snapshot;
    if (snapshot == null) {
      // implicit contract: if snapshot is null, editor is already disposed
      throwEditorDisposed.run();
    }
    return snapshot;
  }

  private @NotNull LogicalLines getSnapshotWithLine(@NotNull LogicalLines snapshot, int line) {
    LogicalLines newSnapshot = snapshot.withLine(line);
    if (snapshot != newSnapshot) {
      SNAPSHOT_UPDATER.compareAndSet(this, snapshot, newSnapshot);
    }
    return newSnapshot;
  }

  private static int getAdjustedLineNumber(@NotNull DocumentSnapshot document, int offset) {
    return document.textLength() == 0 ? -1 : document.lineNumber(offset);
  }

  // text for which offset<->logicalColumn conversion is trivial
  private static boolean isSimpleText(@NotNull CharSequence text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\t' || Character.MIN_SURROGATE <= c && c <= Character.MAX_SURROGATE) {
        return false;
      }
    }
    return true;
  }
}
