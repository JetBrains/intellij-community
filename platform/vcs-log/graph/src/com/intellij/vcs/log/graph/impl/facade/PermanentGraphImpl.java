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


import com.intellij.openapi.util.Condition;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.impl.permanent.PermanentCommitsInfo;
import com.intellij.vcs.log.graph.impl.permanent.PermanentLinearGraphBuilder;
import com.intellij.vcs.log.graph.impl.permanent.PermanentLinearGraphImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class PermanentGraphImpl<CommitId> implements PermanentGraph<CommitId> {

  @NotNull
  public static <CommitId> PermanentGraphImpl<CommitId> newInstance(@NotNull List<? extends GraphCommit<CommitId>> graphCommits) {
    PermanentCommitsInfo<CommitId> commitIdPermanentCommitsInfo = PermanentCommitsInfo.newInstance(graphCommits);

    PermanentLinearGraphBuilder<CommitId> permanentLinearGraphBuilder = PermanentLinearGraphBuilder.newInstance(graphCommits);
    PermanentLinearGraphImpl linearGraph = permanentLinearGraphBuilder.build();
    Map<CommitId, GraphCommit<CommitId>> commitsWithNotLoadParent = permanentLinearGraphBuilder.getCommitsWithNotLoadParent();
    return new PermanentGraphImpl<CommitId>(commitIdPermanentCommitsInfo, linearGraph, commitsWithNotLoadParent);
  }

  @NotNull
  private final PermanentCommitsInfo<CommitId> myPermanentCommitsInfo;

  @NotNull
  private final PermanentLinearGraphImpl myLinearGraph;

  @NotNull
  private final Map<CommitId, GraphCommit<CommitId>> myCommitsWithNotLoadParent;

  public PermanentGraphImpl(@NotNull PermanentCommitsInfo<CommitId> permanentCommitsInfo,
                            @NotNull PermanentLinearGraphImpl linearGraph,
                            @NotNull Map<CommitId, GraphCommit<CommitId>> commitsWithNotLoadParent) {
    myPermanentCommitsInfo = permanentCommitsInfo;
    myLinearGraph = linearGraph;
    myCommitsWithNotLoadParent = commitsWithNotLoadParent;
  }

  @NotNull
  @Override
  public VisibleGraph<CommitId> setFilter(@Nullable Set<CommitId> headsOfVisibleBranches, @Nullable Condition<CommitId> filter) {
    return null;
  }

  @NotNull
  @Override
  public List<GraphCommit<CommitId>> getAllCommits() {
    return null;
  }

  @NotNull
  @Override
  public List<CommitId> getChildren(@NotNull CommitId commit) {
    return null;
  }

  @NotNull
  @Override
  public Set<CommitId> getContainingBranches(@NotNull CommitId commit) {
    return null;
  }
}
