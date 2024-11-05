// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade.sort.bek;

import com.intellij.vcs.log.graph.utils.IntIntMultiMap;
import org.jetbrains.annotations.NotNull;

class BekEdgeRestrictions {
  @NotNull private final IntIntMultiMap myUpToEdge = new IntIntMultiMap();

  @NotNull private final IntIntMultiMap myDownToEdge = new IntIntMultiMap();

  void addRestriction(int upNode, int downNode) {
    myUpToEdge.putValue(upNode, downNode);
    myDownToEdge.putValue(downNode, upNode);
  }

  void removeRestriction(int downNode) {
    for (int upNode : myDownToEdge.get(downNode)) {
      myUpToEdge.remove(upNode, downNode);
    }
  }

  boolean hasRestriction(int upNode) {
    return myUpToEdge.containsKey(upNode);
  }
}
