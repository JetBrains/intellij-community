// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade.sort.bek;

import com.intellij.vcs.log.graph.utils.IntIntMultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntConsumer;

class BekEdgeRestrictions {
  private final @NotNull IntIntMultiMap myUpToEdge = new IntIntMultiMap();

  private final @NotNull IntIntMultiMap myDownToEdge = new IntIntMultiMap();

  void addRestriction(int upNode, int downNode) {
    myUpToEdge.putValue(upNode, downNode);
    myDownToEdge.putValue(downNode, upNode);
  }

  /**
   * Removes the restrictions that point to {@code downNode}.
   *
   * @param releasedUpNodeConsumer receives each up node that has no restriction left after the removal
   */
  void removeRestriction(int downNode, @NotNull IntConsumer releasedUpNodeConsumer) {
    for (int upNode : myDownToEdge.getAsArray(downNode)) {
      boolean hadRestriction = myUpToEdge.containsKey(upNode);
      myUpToEdge.remove(upNode, downNode);
      if (hadRestriction && !myUpToEdge.containsKey(upNode)) {
        releasedUpNodeConsumer.accept(upNode);
      }
    }
  }

  boolean hasRestriction(int upNode) {
    return myUpToEdge.containsKey(upNode);
  }
}
