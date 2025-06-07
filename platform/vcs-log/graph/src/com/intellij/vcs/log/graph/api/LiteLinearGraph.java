// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.api;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.vcs.log.graph.api.EdgeFilter.*;

public interface LiteLinearGraph {
  int nodesCount();

  @NotNull
  List<Integer> getNodes(int nodeIndex, NodeFilter filter);

  enum NodeFilter {
    UP(true, false, NORMAL_UP),
    DOWN(false, true, NORMAL_DOWN),
    ALL(true, true, NORMAL_ALL);

    public final boolean up;
    public final boolean down;
    public final @NotNull EdgeFilter edgeFilter;

    NodeFilter(boolean up, boolean down, @NotNull EdgeFilter edgeFilter) {
      this.up = up;
      this.down = down;
      this.edgeFilter = edgeFilter;
    }

    public static NodeFilter filter(boolean isUp) {
      return isUp ? UP : DOWN;
    }
  }
}
