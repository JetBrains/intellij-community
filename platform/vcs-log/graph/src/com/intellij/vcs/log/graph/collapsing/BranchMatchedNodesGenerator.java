/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph.collapsing;

import com.intellij.vcs.log.graph.api.LiteLinearGraph;
import com.intellij.vcs.log.graph.impl.permanent.PermanentLinearGraphImpl;
import com.intellij.vcs.log.graph.utils.DfsUtil;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.graph.utils.UnsignedBitSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class BranchMatchedNodesGenerator {

  @NotNull
  public static UnsignedBitSet generateVisibleNodes(@NotNull PermanentLinearGraphImpl permanentGraph,
                                                    @Nullable Set<Integer> headNodeIndexes) {
    if (headNodeIndexes == null) {
      UnsignedBitSet nodesVisibility = new UnsignedBitSet();
      nodesVisibility.set(0, permanentGraph.nodesCount() - 1, true);
      return nodesVisibility;
    }

    assert !headNodeIndexes.isEmpty();
    BranchMatchedNodesGenerator generator = new BranchMatchedNodesGenerator(LinearGraphUtils.asLiteLinearGraph(permanentGraph));
    generator.generate(headNodeIndexes);
    return generator.myNodesVisibility;
  }

  @NotNull private final LiteLinearGraph myGraph;

  @NotNull private final UnsignedBitSet myNodesVisibility;

  @NotNull private final DfsUtil myDfsUtil = new DfsUtil();

  BranchMatchedNodesGenerator(@NotNull LiteLinearGraph graph) {
    myGraph = graph;
    myNodesVisibility = new UnsignedBitSet();
  }

  private void generate(@NotNull Set<Integer> startedNodes) {
    for (int startNode : startedNodes) {
      myNodesVisibility.set(startNode, true);
      if (startNode < 0) continue;
      myDfsUtil.nodeDfsIterator(startNode, new DfsUtil.NextNode() {
        @Override
        public int fun(int currentNode) {
          for (int downNode : myGraph.getNodes(currentNode, LiteLinearGraph.NodeFilter.DOWN)) {
            if (!myNodesVisibility.get(downNode)) {
              myNodesVisibility.set(downNode, true);
              return downNode;
            }
          }
          return NODE_NOT_FOUND;
        }
      });
    }
  }
}
