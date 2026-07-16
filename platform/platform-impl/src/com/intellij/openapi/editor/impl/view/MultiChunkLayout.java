// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

final class MultiChunkLayout extends LineLayout {
  private final LineBidiRun[] runsInLogicalOrder;
  private final LineBidiRun[] runsInVisualOrder;

  MultiChunkLayout(LineBidiRun @NotNull [] runsInLogicalOrder, LineBidiRun @NotNull [] runsInVisualOrder) {
    this.runsInLogicalOrder = runsInLogicalOrder;
    this.runsInVisualOrder = runsInVisualOrder;
  }

  @Override
  Stream<LineChunk> getChunksInLogicalOrder() {
    return Stream.of(runsInLogicalOrder).flatMap(LineBidiRun::chunkStream);
  }

  @Override
  boolean isLtr() {
    return runsInLogicalOrder.length == 0 ||
           runsInLogicalOrder.length == 1 && !runsInLogicalOrder[0].isRtl();
  }

  @Override
  boolean isRtlLocation(int offset, boolean leanForward) {
    if (offset == 0 && !leanForward) {
      return false;
    }
    for (LineBidiRun run : runsInLogicalOrder) {
      if (offset < run.getEndOffset() || offset == run.getEndOffset() && !leanForward) {
        return run.isRtl();
      }
    }
    return false;
  }

  @Override
  int findNearestDirectionBoundary(int offset, boolean lookForward) {
    byte originLevel = -1;
    if (lookForward) {
      for (LineBidiRun run : runsInLogicalOrder) {
        if (originLevel >= 0) {
          if (run.getLevel() != originLevel) {
            return run.getStartOffset();
          }
        } else if (run.getEndOffset() > offset) {
          originLevel = run.getLevel();
        }
      }
      return originLevel > 0 ? runsInLogicalOrder[runsInLogicalOrder.length - 1].getEndOffset() : -1;
    } else {
      for (int i = runsInLogicalOrder.length - 1; i >= 0; i--) {
        LineBidiRun run = runsInLogicalOrder[i];
        if (originLevel >= 0) {
          if (run.getLevel() != originLevel) {
            return run.getEndOffset();
          }
        } else if (run.getStartOffset() < offset) {
          originLevel = run.getLevel();
        }
      }
      return originLevel > 0 ? 0 : -1;
    }
  }

  @Override
  LineBidiRun @NotNull [] getRunsInLogicalOrder() {
    return runsInLogicalOrder;
  }

  @Override
  LineBidiRun @NotNull [] getRunsInVisualOrder() {
    return runsInVisualOrder;
  }
}
