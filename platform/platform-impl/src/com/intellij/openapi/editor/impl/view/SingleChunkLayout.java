// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.function.Consumer;

final class SingleChunkLayout extends LineLayout {
  private final @Nullable LineChunk chunk;

  SingleChunkLayout(@Nullable LineChunk chunk) {
    this.chunk = chunk;
  }

  @Override
  void forEachChunk(@NotNull Consumer<? super LineChunk> action) {
    LineChunk chunk = this.chunk;
    if (chunk != null) {
      action.accept(chunk);
    }
  }

  @Override
  boolean isLtr() {
    return true;
  }

  @Override
  boolean isRtlLocation(int offset, boolean leanForward) {
    return false;
  }

  @Override
  int findNearestDirectionBoundary(int offset, boolean lookForward) {
    return -1;
  }

  @Override
  LineBidiRun @NotNull [] getRunsInLogicalOrder() {
    return createRuns();
  }

  @Override
  LineBidiRun @NotNull [] getRunsInVisualOrder() {
    return createRuns();
  }

  private LineBidiRun[] createRuns() {
    if (chunk == null) {
      return LineBidiRun.EMPTY_ARRAY;
    }
    LineBidiRun run = new LineBidiRun(0, chunk.getEndOffset(), (byte) 0, 0, Collections.singletonList(chunk));
    return new LineBidiRun[] { run };
  }
}
