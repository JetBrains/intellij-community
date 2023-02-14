// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.visible.EmptyVisibleGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class EmptyPermanentGraph implements PermanentGraph<Integer> {

  private static final PermanentGraph<Integer> INSTANCE = new EmptyPermanentGraph();

  public static @NotNull PermanentGraph<Integer> getInstance() {
    return INSTANCE;
  }

  @Override
  public @NotNull VisibleGraph<Integer> createVisibleGraph(@NotNull SortType sortType, @Nullable Set<? extends Integer> headsOfVisibleBranches,
                                                           @Nullable Set<? extends Integer> filter) {
    return EmptyVisibleGraph.getInstance();
  }

  @Override
  public @NotNull List<GraphCommit<Integer>> getAllCommits() {
    return Collections.emptyList();
  }

  @Override
  public @NotNull List<Integer> getChildren(@NotNull Integer commit) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull Set<Integer> getContainingBranches(@NotNull Integer commit) {
    return Collections.emptySet();
  }

  @Override
  public @NotNull Condition<Integer> getContainedInBranchCondition(@NotNull Collection<? extends Integer> currentBranchHead) {
    return Conditions.alwaysFalse();
  }
}
