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

package com.intellij.vcs.log.newgraph.facade;

import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.newgraph.utils.DfsUtil;
import com.intellij.vcs.log.facade.utils.Flags;
import com.intellij.vcs.log.newgraph.utils.MyUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class ContainingBranchesGetter {
  @NotNull
  private final PermanentGraph myPermanentGraph;

  @NotNull
  private final Set<Integer> myBranchNodeIndexes;

  @NotNull
  private final DfsUtil myDfsUtil;

  @NotNull
  private final Flags myTempFlags;

  public ContainingBranchesGetter(@NotNull PermanentGraph permanentGraph,
                                  @NotNull Set<Integer> branchNodeIndexes,
                                  @NotNull DfsUtil dfsUtil,
                                  @NotNull Flags tempFlags) {
    myPermanentGraph = permanentGraph;
    myBranchNodeIndexes = branchNodeIndexes;
    myDfsUtil = dfsUtil;
    myTempFlags = tempFlags;
  }

  public Set<Integer> getBranchHashIndexes(int nodeIndex) {
    final Set<Integer> result = new HashSet<Integer>();

    MyUtils.setAllValues(myTempFlags, false);
    myTempFlags.set(nodeIndex, true);
    checkAndAdd(nodeIndex, result);
    myDfsUtil.nodeDfsIterator(nodeIndex, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
        for (int upNode : myPermanentGraph.getUpNodes(currentNode)) {
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
    if (myBranchNodeIndexes.contains(nodeIndex))
      result.add(myPermanentGraph.getHashIndex(nodeIndex));
  }
}
