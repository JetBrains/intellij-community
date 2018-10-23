// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * {@link RangeMarkerTree} with intervals which are not collected when no one holds a reference to them.
 *
 * @see RangeMarkerWithGetterImpl
 */
class HardReferencingRangeMarkerTree<T extends RangeMarkerWithGetterImpl> extends RangeMarkerTree<T> {
  HardReferencingRangeMarkerTree(@NotNull Document document) {
    super(document);
  }

  @NotNull
  @Override
  protected Node<T> createNewNode(@NotNull T key,
                                    int start,
                                    int end,
                                    boolean greedyToLeft,
                                    boolean greedyToRight,
                                    boolean stickingToRight,
                                    int layer) {
    return new Node<>(this, key, start, end, greedyToLeft, greedyToRight, stickingToRight);
  }

  static class Node<T extends RangeMarkerWithGetterImpl> extends RMNode<T> {
    Node(@NotNull RangeMarkerTree<T> rangeMarkerTree,
         @NotNull T key,
         int start,
         int end,
         boolean greedyToLeft,
         boolean greedyToRight,
         boolean stickingToRight) {
      super(rangeMarkerTree, key, start, end, greedyToLeft, greedyToRight, stickingToRight);
    }

    @Override
    protected Getter<T> createGetter(@NotNull T interval) {
      //noinspection unchecked
      return (Getter<T>) interval;
    }
  }
}
