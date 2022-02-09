// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.annotate;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AnnotatedLineModificationDetails {
  /**
   * Line content after the annotated commit. Might not match with local line (ie: if whitespace changes were ignored).
   */
  public final @NotNull String lineContentAfter;
  /**
   * Changed ranges in annotated commit.
   */
  public final @NotNull List<InnerChange> changes;

  public AnnotatedLineModificationDetails(@NotNull String lineContentAfter, @NotNull List<InnerChange> changes) {
    this.lineContentAfter = lineContentAfter;
    this.changes = changes;
  }

  public static class InnerChange {
    public final int startOffset;
    public final int endOffset;
    public final @NotNull InnerChangeType type;

    public InnerChange(int startOffset, int endOffset, @NotNull InnerChangeType type) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.type = type;
    }
  }

  public enum InnerChangeType {DELETED, INSERTED, MODIFIED}
}
