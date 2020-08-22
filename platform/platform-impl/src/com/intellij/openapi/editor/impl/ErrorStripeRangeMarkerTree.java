// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

class ErrorStripeRangeMarkerTree extends HardReferencingRangeMarkerTree<ErrorStripeMarkerImpl> {

  ErrorStripeRangeMarkerTree(@NotNull Document document) {
    super(document);
  }

  @Override
  protected int compareEqualStartIntervals(@NotNull IntervalNode<ErrorStripeMarkerImpl> i1, @NotNull IntervalNode<ErrorStripeMarkerImpl> i2) {
    Node o1 = (Node)i1;
    Node o2 = (Node)i2;
    int d = o2.myLayer - o1.myLayer;
    if (d != 0) {
      return d;
    }
    return super.compareEqualStartIntervals(i1, i2);
  }

  @NotNull
  @Override
  protected Node createNewNode(@NotNull ErrorStripeMarkerImpl key, int start, int end,
                               boolean greedyToLeft, boolean greedyToRight, boolean stickingToRight, int layer) {
    return new Node(this, key, start, end, greedyToLeft, greedyToRight, stickingToRight, layer);
  }

  static class Node extends RMNode<ErrorStripeMarkerImpl> {
    final int myLayer;

    Node(@NotNull ErrorStripeRangeMarkerTree rangeMarkerTree,
         @NotNull final ErrorStripeMarkerImpl key,
         int start,
         int end,
         boolean greedyToLeft,
         boolean greedyToRight,
         boolean stickingToRight,
         int layer) {
      super(rangeMarkerTree, key, start, end, greedyToLeft, greedyToRight, stickingToRight);
      myLayer = layer;
    }

  }
}