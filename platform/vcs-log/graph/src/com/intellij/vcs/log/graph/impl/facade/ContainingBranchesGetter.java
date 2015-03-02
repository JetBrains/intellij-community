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
import com.intellij.vcs.log.graph.api.LiteLinearGraph;
import com.intellij.vcs.log.graph.utils.DfsUtil;
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class ContainingBranchesGetter {
  @NotNull private final LiteLinearGraph myGraph;

  @NotNull private final Set<Integer> myBranchNodeIndexes;

  @NotNull private final DfsUtil myDfsUtil = new DfsUtil();

  @NotNull private final Flags myTempFlags;

  public ContainingBranchesGetter(@NotNull LiteLinearGraph graph, @NotNull Set<Integer> branchNodeIndexes) {
    myGraph = graph;
    myBranchNodeIndexes = branchNodeIndexes;
    myTempFlags = new BitSetFlags(graph.nodesCount());
  }

  public Set<Integer> getBranchNodeIndexes(int nodeIndex) {
    final Set<Integer> result = new HashSet<Integer>();

    myTempFlags.setAll(false);
    myTempFlags.set(nodeIndex, true);
    checkAndAdd(nodeIndex, result);
    myDfsUtil.nodeDfsIterator(nodeIndex, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
        for (int upNode : myGraph.getNodes(currentNode, LiteLinearGraph.NodeFilter.UP)) {
          if (!myTempFlags.get(upNode)) {
            myTempFlags.set(upNode, true);
            checkAndAdd(upNode, result);
            return upNode;
          }
        }

        return NODE_NOT_FOUND;
      }
    });

    return result;
  }

  private void checkAndAdd(int nodeIndex, Set<Integer> result) {
    if (myBranchNodeIndexes.contains(nodeIndex)) result.add(nodeIndex);
  }

  public void walkBranch(int branchHead, @NotNull final Consumer<Integer> consumer) {
    myTempFlags.setAll(false);
    myTempFlags.set(branchHead, true);
    consumer.consume(branchHead);

    myDfsUtil.nodeDfsIterator(branchHead, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
        for (int downNode : myGraph.getNodes(currentNode, LiteLinearGraph.NodeFilter.DOWN)) {
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
