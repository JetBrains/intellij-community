// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.impl.FileCacheConflictCollector.AutomergeResult;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jetbrains.annotations.NotNull;

final class ResolvedConflict implements MergeAttempt {
  private final @NotNull CharSequence text;
  private final long expectedDocumentStamp;
  private boolean applied;

  ResolvedConflict(@NotNull CharSequence text, long expectedDocumentStamp) {
    this.text = text;
    this.expectedDocumentStamp = expectedDocumentStamp;
  }

  /**
   * Call before the reload. The reload gives the document the stamp of the file, so the answer is false after it.
   */
  boolean isUpToDate(@NotNull Document document) {
    return expectedDocumentStamp == document.getModificationStamp();
  }

  @RequiresWriteLock
  void applyTo(@NotNull Document document) {
    document.setText(text);
    applied = true;
  }

  @Override
  public @NotNull AutomergeResult result() {
    return applied ? AutomergeResult.MERGED : AutomergeResult.NOT_APPLIED;
  }
}
