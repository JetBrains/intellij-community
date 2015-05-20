/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    @NotNull public final EdgeFilter edgeFilter;

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
