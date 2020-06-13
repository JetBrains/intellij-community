// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.impl.facade.bek;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.vcs.log.graph.api.LinearGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getDownNodes;
import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getUpNodes;

public final class BekChecker {
  private final static Logger LOG = Logger.getInstance(BekChecker.class);

  public static void checkLinearGraph(@NotNull LinearGraph linearGraph) {
    Pair<Integer, Integer> reversedEdge = findReversedEdge(linearGraph);
    if (reversedEdge != null) {
      LOG.error("Illegal edge: up node " + reversedEdge.first + ", downNode " + reversedEdge.second);
    }
  }

  @Nullable
  public static Pair<Integer, Integer> findReversedEdge(@NotNull LinearGraph linearGraph) {
    for (int i = 0; i < linearGraph.nodesCount(); i++) {
      for (int downNode : getDownNodes(linearGraph, i)) {
        if (downNode <= i) {
          return Pair.create(i, downNode);
        }
      }

      for (int upNode : getUpNodes(linearGraph, i)) {
        if (upNode >= i) {
          return Pair.create(upNode, i);
        }
      }
    }
    return null;
  }
}
