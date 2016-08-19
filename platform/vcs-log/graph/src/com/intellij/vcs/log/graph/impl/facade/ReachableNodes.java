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

package com.intellij.vcs.log.graph.impl.facade;

import com.intellij.util.Consumer;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.LiteLinearGraph;
import com.intellij.vcs.log.graph.utils.DfsUtil;
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.graph.utils.UnsignedBitSet;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ReachableNodes {
  @NotNull private final LiteLinearGraph myGraph;
  @NotNull private final DfsUtil myDfsUtil = new DfsUtil();
  @NotNull private final Flags myTempFlags;

  public ReachableNodes(@NotNull LiteLinearGraph graph) {
    myGraph = graph;
    myTempFlags = new BitSetFlags(graph.nodesCount());
  }

  @NotNull
  public static UnsignedBitSet getReachableNodes(@NotNull LinearGraph permanentGraph, @Nullable Set<Integer> headNodeIndexes) {
    if (headNodeIndexes == null) {
      UnsignedBitSet nodesVisibility = new UnsignedBitSet();
      nodesVisibility.set(0, permanentGraph.nodesCount() - 1, true);
      return nodesVisibility;
    }

    final UnsignedBitSet result = new UnsignedBitSet();
    ReachableNodes getter = new ReachableNodes(LinearGraphUtils.asLiteLinearGraph(permanentGraph));
    getter.walk(headNodeIndexes, new Consumer<Integer>() {
      @Override
      public void consume(Integer node) {
        result.set(node, true);
      }
    });

    return result;
  }

  public Set<Integer> getContainingBranches(int nodeIndex, @NotNull final Collection<Integer> branchNodeIndexes) {
    final Set<Integer> result = new HashSet<>();

    walk(Collections.singletonList(nodeIndex), false, new Consumer<Integer>() {
      @Override
      public void consume(Integer integer) {
        if (branchNodeIndexes.contains(integer)) result.add(integer);
      }
    });

    return result;
  }

  public void walk(@NotNull Collection<Integer> headIds, @NotNull final Consumer<Integer> consumer) {
    walk(headIds, true, consumer);
  }

  private void walk(@NotNull Collection<Integer> startNodes, final boolean goDown, @NotNull final Consumer<Integer> consumer) {
    synchronized (myTempFlags) {

      myTempFlags.setAll(false);
      for (int start : startNodes) {
        if (start < 0) continue;
        if (myTempFlags.get(start)) continue;
        myTempFlags.set(start, true);
        consumer.consume(start);

        myDfsUtil.nodeDfsIterator(start, new DfsUtil.NextNode() {
          @Override
          public int fun(int currentNode) {
            for (int downNode : myGraph.getNodes(currentNode, goDown ? LiteLinearGraph.NodeFilter.DOWN : LiteLinearGraph.NodeFilter.UP)) {
              if (!myTempFlags.get(downNode)) {
                myTempFlags.set(downNode, true);
                consumer.consume(downNode);
                return downNode;
              }
            }

            return NODE_NOT_FOUND;
          }
        });
      }
    }
  }
}
