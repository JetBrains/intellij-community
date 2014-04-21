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
package com.intellij.vcs.log.graph.impl.facade.bek;

import com.intellij.openapi.util.Condition;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.GraphColorManager;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DelegatedPermanentGraphInfo<CommitId> implements PermanentGraphInfo<CommitId> {

  @NotNull
  private final PermanentGraphInfo<CommitId> myDelegateInfo;
  @NotNull
  private final BekIntMap myBekIntMap;

  public DelegatedPermanentGraphInfo(@NotNull PermanentGraphInfo<CommitId> delegateInfo, @NotNull BekIntMap bekIntMap) {
    myDelegateInfo = delegateInfo;
    myBekIntMap = bekIntMap;
  }

  @NotNull
  @Override
  public PermanentCommitsInfo<CommitId> getPermanentCommitsInfo() {
    final PermanentCommitsInfo<CommitId> commitsInfo = myDelegateInfo.getPermanentCommitsInfo();
    return new PermanentCommitsInfo<CommitId>() {
      @NotNull
      @Override
      public CommitId getCommitId(int permanentNodeIndex) {
        return commitsInfo.getCommitId(myBekIntMap.getUsualIndex(permanentNodeIndex));
      }

      @Override
      public long getTimestamp(int permanentNodeIndex) {
        return commitsInfo.getTimestamp(myBekIntMap.getUsualIndex(permanentNodeIndex));
      }

      @Override
      public int getPermanentNodeIndex(@NotNull CommitId commitId) {
        int usualIndex = commitsInfo.getPermanentNodeIndex(commitId);
        return myBekIntMap.getBekIndex(usualIndex);
      }

      @NotNull
      @Override
      public Set<Integer> convertToCommitIndexes(Collection<CommitId> heads) {
        Set<Integer> usualIndexes = commitsInfo.convertToCommitIndexes(heads);
        return ContainerUtil.map2Set(usualIndexes, new Function<Integer, Integer>() {
          @Override
          public Integer fun(Integer integer) {
            return myBekIntMap.getBekIndex(integer);
          }
        });
      }
    };
  }

  @NotNull
  @Override
  public LinearGraph getPermanentLinearGraph() {
    final LinearGraph linearGraph = myDelegateInfo.getPermanentLinearGraph();
    return new LinearGraph() {
      @Override
      public int nodesCount() {
        return linearGraph.nodesCount();
      }

      @NotNull
      private List<Integer> convertToBekIndexes(@NotNull List<Integer> usualIndexes) {
        return ContainerUtil.map(usualIndexes, new Function<Integer, Integer>() {
          @Override
          public Integer fun(Integer integer) {
            return myBekIntMap.getBekIndex(integer);
          }
        });
      }

      @NotNull
      @Override
      public List<Integer> getUpNodes(int nodeIndex) {
        return convertToBekIndexes(linearGraph.getUpNodes(myBekIntMap.getUsualIndex(nodeIndex)));
      }

      @NotNull
      @Override
      public List<Integer> getDownNodes(int nodeIndex) {
        return convertToBekIndexes(linearGraph.getDownNodes(myBekIntMap.getUsualIndex(nodeIndex)));
      }
    };
  }

  @NotNull
  @Override
  public GraphLayout getPermanentGraphLayout() {
    final GraphLayout graphLayout = myDelegateInfo.getPermanentGraphLayout();
    return new GraphLayout() {
      @Override
      public int getLayoutIndex(int nodeIndex) {
        return graphLayout.getLayoutIndex(myBekIntMap.getUsualIndex(nodeIndex));
      }

      @Override
      public int getOneOfHeadNodeIndex(int nodeIndex) {
        int usualIndex = graphLayout.getOneOfHeadNodeIndex(myBekIntMap.getUsualIndex(nodeIndex));
        return myBekIntMap.getBekIndex(usualIndex);
      }
    };
  }

  @NotNull
  @Override
  public Condition<Integer> getNotCollapsedNodes() {
    final Condition<Integer> notCollapsedNodes = myDelegateInfo.getNotCollapsedNodes();
    return new Condition<Integer>() {
      @Override
      public boolean value(Integer bekIndex) {
        return notCollapsedNodes.value(myBekIntMap.getUsualIndex(bekIndex));
      }
    };
  }

  @NotNull
  @Override
  public GraphColorManager<CommitId> getGraphColorManager() {
    return myDelegateInfo.getGraphColorManager();
  }

  @NotNull
  @Override
  public Map<CommitId, GraphCommit<CommitId>> getCommitsWithNotLoadParent() {
    return myDelegateInfo.getCommitsWithNotLoadParent();
  }
}
