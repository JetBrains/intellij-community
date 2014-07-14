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

package com.intellij.vcs.log.graph.impl.visible.adapters;

import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraphWithCommitInfo;
import com.intellij.vcs.log.graph.api.LinearGraphWithHiddenNodes;
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo;
import org.jetbrains.annotations.NotNull;

public class GraphWithHiddenNodesAsGraphWithCommitInfo<CommitId> extends GraphWithHiddenNodesAsPrintedGraph implements LinearGraphWithCommitInfo<CommitId> {

  @NotNull
  protected final PermanentCommitsInfo<CommitId> myPermanentCommitsInfo;

  public GraphWithHiddenNodesAsGraphWithCommitInfo(@NotNull LinearGraphWithHiddenNodes delegateGraph,
                                                   @NotNull GraphLayout permanentGraphLayout,
                                                   @NotNull PermanentCommitsInfo<CommitId> permanentCommitsInfo) {
    super(delegateGraph, permanentGraphLayout);
    myPermanentCommitsInfo = permanentCommitsInfo;
  }

  @Override
  public CommitId getHashIndex(int nodeIndex) {
    return myPermanentCommitsInfo.getCommitId(getIndexInPermanentGraph(nodeIndex));
  }

  @Override
  public long getTimestamp(int nodeIndex) {
    return myPermanentCommitsInfo.getTimestamp(getIndexInPermanentGraph(nodeIndex));
  }

  @Override
  public CommitId getOneOfHeads(int nodeIndex) {
    int oneOfHeadNodeIndex = myPermanentGraphLayout.getOneOfHeadNodeIndex(getIndexInPermanentGraph(nodeIndex));
    return myPermanentCommitsInfo.getCommitId(oneOfHeadNodeIndex);
  }

  @Override
  public int getNodeIndex(CommitId commitId) {
    int permanentNodeIndex = myPermanentCommitsInfo.getPermanentNodeIndex(commitId);
    if (permanentNodeIndex == -1)
      return -1;
    return myIntToIntMap.getShortIndex(permanentNodeIndex);
  }
}
