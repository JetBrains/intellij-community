// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.stream.Stream;

final class SingleChunkLayout extends LineLayout {
  private final @Nullable LineChunk myChunk;

  SingleChunkLayout(@Nullable LineChunk chunk) {
    myChunk = chunk;
  }

  @Override
  Stream<LineChunk> getChunksInLogicalOrder() {
    return myChunk == null ? Stream.empty() : Stream.of(myChunk);
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
  LineBidiRun[] getRunsInLogicalOrder() {
    return createRuns();
  }

  @Override
  LineBidiRun[] getRunsInVisualOrder() {
    return createRuns();
  }

  private LineBidiRun[] createRuns() {
    if (myChunk == null) {
      return LineBidiRun.EMPTY_ARRAY;
    }
    LineBidiRun run = new LineBidiRun(0, myChunk.getEndOffset(), (byte) 0, Collections.singletonList(myChunk));
    return new LineBidiRun[]{run};
  }
}
