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

import java.awt.Graphics2D;
import java.awt.Point;
import java.util.Collection;
import java.util.List;

/**
 * The only point of interaction with the Graph.
 * <p/>
 * Any access to the methods of this class must be performed from the EDT.
 */
public interface GraphBlackBox {

  void paint(Graphics2D g, int visibleRow);

  /**
   * Performs some user action on the graph, which can be a click, hover, drag, changing view parameters, etc. <br/>
   * Changes are applied to the graph immediately, and the method returns the result of such action. <br/>
   * In some cases the action might take significant time (say, 1 second) - clients of this method take care of it themselves: by showing
   * some modal progress or else.
   */
  @Nullable
  GraphChangeEvent performAction(@NotNull GraphAction action);

  /**
   * Returns all commits in the graph without considering commits visibility.
   * @see #getVisibleCommits()
   */
  @NotNull
  List<Integer> getAllCommits();

  /**
   * Returns those commits of the graph which is current visible, according to filters currently set up on the graph.
   * @see #getAllCommits()
   * @see #setFilter(Condition)
   * @see #setVisibleBranches(Collection)
   */
  @NotNull
  List<Integer> getVisibleCommits();

  /**
   * A shorthand to getVisibleCommits().get(visibleRow), but may be faster.
   */
  int getCommitAtRow(int visibleRow);

  /**
   * A shorthand to {@code getVisibleCommits().size()}, but is faster.
   */
  int getVisibleCommitCount();

  void setVisibleBranches(@Nullable Collection<Integer> heads);

  void setFilter(@NotNull Condition<Integer> visibilityPredicate);

  @NotNull
  GraphInfoProvider getInfoProvider();

  class HoverGraphAction implements GraphAction {
    int visibleRow;
    Point relativePoint;
  }

  interface GraphChangeEvent {
  }

}
