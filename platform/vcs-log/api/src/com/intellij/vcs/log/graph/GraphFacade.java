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
package com.intellij.vcs.log.graph;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * The only point of interaction with the Graph.
 * <p/>
 * Any access to the methods of this class must be performed from the EDT.
 */
public interface GraphFacade {

  /**
   * Paints the given row.
   */
  @NotNull
  PaintInfo paint(int visibleRow);

  /**
   * Performs some user action on the graph, which can be a click, hover, drag, changing view parameters, etc. <br/>
   * Changes are applied to the graph immediately, and the method returns the result of such action. <br/>
   * In some cases the action might take significant time (say, 1 second) - clients of this method take care of it themselves: by showing
   * some modal progress or else.
   */
  @Nullable
  GraphAnswer performAction(@NotNull GraphAction action);

  /**
   * Returns all commits in the graph without considering commits visibility.
   * To get visible commits use {@link #getVisibleCommitCount} & {@link #getCommitAtRow}.
   */
  @NotNull
  List<GraphCommit<Integer>> getAllCommits();

  /**
   * A shorthand to getVisibleCommits().get(visibleRow), but may be faster.
   */
  int getCommitAtRow(int visibleRow);

  /**
   * A shorthand to {@code getVisibleCommits().size()}, but is faster.
   */
  int getVisibleCommitCount();

  /**
   * Set branches which should be visible in the log, all others will be hidden.
   * Pass {@code null} to show all branches, i.e. reset this branch filter.
   *
   * @param heads branches represented by commit indices of commits they point to; or {@code null} if all branches should be shown.
   * @see #setFilter(Condition)
   */
  void setVisibleBranches(@Nullable Collection<Integer> heads);

  /**
   * Set filter to the commits displayed by the log.
   *
   * @param visibilityPredicate check if the given commit should be shown or not. null means no filter.
   * @see #setVisibleBranches(Collection)
   */
  void setFilter(@Nullable Condition<Integer> visibilityPredicate);

  /**
   * Returns the provider of some information about the graph.
   */
  @NotNull
  GraphInfoProvider getInfoProvider();

}
