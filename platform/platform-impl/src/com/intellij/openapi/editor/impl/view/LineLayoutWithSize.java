// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

final class LineLayoutWithSize extends LineLayout {
  private final LineLayout myDelegate;
  private final float myWidth;

  LineLayoutWithSize(@NotNull LineLayout delegate) {
    myDelegate = delegate;
    myWidth = calculateWidth();
  }

  @Override
  Stream<LineChunk> getChunksInLogicalOrder() {
    return myDelegate.getChunksInLogicalOrder();
  }

  @Override
  float getWidth() {
    return myWidth;
  }

  @Override
  boolean isLtr() {
    return myDelegate.isLtr();
  }

  @Override
  boolean isRtlLocation(int offset, boolean leanForward) {
    return myDelegate.isRtlLocation(offset, leanForward);
  }

  @Override
  int findNearestDirectionBoundary(int offset, boolean lookForward) {
    return myDelegate.findNearestDirectionBoundary(offset, lookForward);
  }

  @Override
  LineBidiRun[] getRunsInLogicalOrder() {
    return myDelegate.getRunsInLogicalOrder();
  }

  @Override
  LineBidiRun[] getRunsInVisualOrder() {
    return myDelegate.getRunsInVisualOrder();
  }

  private float calculateWidth() {
    float x = 0;
    for (LineVisualFragment fragment : getFragmentsInVisualOrder(0)) {
      x = fragment.getEndX();
    }
    return x;
  }
}
