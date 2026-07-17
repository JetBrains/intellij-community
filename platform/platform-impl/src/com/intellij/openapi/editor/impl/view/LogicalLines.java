// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.ex.DocumentSnapshot;
import kotlinx.collections.immutable.ExtensionsKt;
import kotlinx.collections.immutable.PersistentList;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Objects;

final class LogicalLines {
  private static final int BATCH_PREFETCH_SIZE = 32; // optimization for sequential reading

  final DocumentSnapshot document;
  final PersistentList<LogicalColumns> lines; // index: lineNumber, value: cachedColumns
  final int tabSize;

  LogicalLines(
    DocumentSnapshot document,
    PersistentList<LogicalColumns> lines,
    int tabSize
  ) {
    this.document = document;
    this.lines = lines;
    this.tabSize = tabSize;
  }

  int offsetToLogicalColumn(int line, int offset) {
    LogicalColumns columns = lines.get(line);
    if (columns == null) {
      throw new NoSuchElementException("withLine should be called first");
    }
    return columns.offsetToLogicalColumn(document, tabSize, line, offset);
  }

  int logicalColumnToOffset(int line, int column) {
    LogicalColumns columns = lines.get(line);
    if (columns == null) {
      throw new NoSuchElementException("withLine should be called first");
    }
    return columns.logicalColumnToOffset(document, tabSize, line, column);
  }

  @NotNull LogicalLines withLine(int line) {
    if (lines.get(line) != null) {
      return this; // cache hit
    }
    PersistentList.Builder<LogicalColumns> builder = lines.builder();
    int startLine = line / BATCH_PREFETCH_SIZE * BATCH_PREFETCH_SIZE;
    int endLine = Math.min(startLine + BATCH_PREFETCH_SIZE, document.lineCount());
    for (int i = startLine; i < endLine; i++) {
      if (lines.get(i) == null) {
        builder.set(i, LogicalColumns.create(document, tabSize, i));
      }
    }
    return new LogicalLines(document, builder.build(), tabSize);
  }

  @NotNull LogicalLines withInvalidatedLines(int newTabSize, boolean force) {
    int oldEndLine = lines.size() - 1;
    int newEndLine = document.lineCount() - 1;
    PersistentList<LogicalColumns> newLines = invalidatedLines(
      0,
      oldEndLine,
      newEndLine,
      !force && oldEndLine == newEndLine
    );
    return new LogicalLines(document, newLines, newTabSize);
  }

  @NotNull LogicalLines withInvalidatedLines(
    @NotNull DocumentSnapshot newDocument,
    int startLine,
    int oldEndLine,
    int newEndLine,
    boolean preserveTrivialLines
  ) {
    PersistentList<LogicalColumns> newLines = invalidatedLines(startLine, oldEndLine, newEndLine, preserveTrivialLines);
    return new LogicalLines(newDocument, newLines, tabSize);
  }

  void validateState() {
    int cacheSize = lines.size();
    if (cacheSize != document.lineCount()) {
      throw new IllegalStateException("Line count: " + document.lineCount() + ", cache size: " + cacheSize);
    }
    for (int i = 0; i < cacheSize; i++) {
      LogicalColumns columns = lines.get(i);
      if (columns != null) {
        LogicalColumns actual = LogicalColumns.create(document, tabSize, i);
        if (!Objects.equals(columns, actual)) {
          throw new IllegalStateException("Wrong cache state at line " + i);
        }
      }
    }
  }

  private @NotNull PersistentList<LogicalColumns> invalidatedLines(
    int startLine,
    int endLine,
    int newEndLine,
    boolean preserveTrivialLines
  ) {
    if (preserveTrivialLines) {
      for (int line = startLine; line <= endLine; line++) {
        LogicalColumns columns = lines.get(line);
        if (columns == null || !columns.isTrivial()) {
          preserveTrivialLines = false;
          break;
        }
      }
    }
    var builder = lines.builder();
    if (!preserveTrivialLines) {
      int endLine0 = Math.min(endLine, newEndLine);
      for (int line = startLine; line <= endLine0; line++) {
        builder.set(line, null);
      }
    }
    if (endLine == newEndLine) {
      return builder.build();
    }
    if (endLine < newEndLine) {
      LogicalColumns columns = preserveTrivialLines ? LogicalColumns.getTrivial() : null;
      builder.addAll(endLine + 1, Collections.nCopies(newEndLine - endLine, columns));
      return builder.build();
    }
    // endLine > newEndLine
    int size = lines.size();
    if (endLine == size - 1) {
      //noinspection ListRemoveInLoop -- can't be replaced because of performance reasons
      for (int line = size - 1; line > newEndLine; line--) {
        builder.remove(line);
      }
      return builder.build();
    } else {
      // removing from the middle is slow, create new list
      var newBuilder = ExtensionsKt.<LogicalColumns>persistentListOf().builder();
      newBuilder.addAll(builder.subList(0, newEndLine + 1));
      newBuilder.addAll(builder.subList(endLine + 1, size));
      return newBuilder.build();
    }
  }
}
