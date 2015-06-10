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
package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.VisibleGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class EmptyPermanentGraph implements PermanentGraph<Integer> {

  private static final PermanentGraph<Integer> INSTANCE = new EmptyPermanentGraph();

  @NotNull
  public static PermanentGraph<Integer> getInstance() {
    return INSTANCE;
  }

  @NotNull
  @Override
  public VisibleGraph<Integer> createVisibleGraph(@NotNull SortType sortType, @Nullable Set<Integer> headsOfVisibleBranches,
                                                  @Nullable Set<Integer> filter) {
    return EmptyVisibleGraph.getInstance();
  }

  @NotNull
  @Override
  public List<GraphCommit<Integer>> getAllCommits() {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<Integer> getChildren(@NotNull Integer commit) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Set<Integer> getContainingBranches(@NotNull Integer commit) {
    return Collections.emptySet();
  }

  @NotNull
  @Override
  public Condition<Integer> getContainedInBranchCondition(@NotNull Collection<Integer> currentBranchHead) {
    return Conditions.alwaysFalse();
  }

}
