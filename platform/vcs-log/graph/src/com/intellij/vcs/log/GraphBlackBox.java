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
package com.intellij.vcs.log;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The only point of interaction with the Graph.
 */
public interface GraphBlackBox {

  void paint(Graphics2D g, int visibleRow);

  @Nullable
  GraphChangeEvent performAction(@NotNull GraphAction action);

  @NotNull
  List<Integer> getAllCommits();

  @NotNull
  List<Integer> getVisibleCommits();

  void setVisibleBranches(@Nullable Collection<Integer> heads);

  void setFilter(Condition<Integer> visibilityPredicate);

  GraphInfoProvider getInfoProvider();

  interface GraphInfoProvider {
    int getOneOfHeads(int commit); // this is fast
    Set<Integer> getContainingBranches(int commitIndex); // this requires graph iteration => can take some time
    RowInfo getRowInfo(int visibleRow);
  }

  /**
   * Some information about row highlighting etc.
   */
  interface RowInfo {

  }

  interface GraphAction {
  }

  class ClickGraphAction implements GraphAction {
    int visibleRow;
    Point relativePoint;
  }

  class HoverGraphAction implements GraphAction {
    int visibleRow;
    Point relativePoint;
  }

  class LinearBranchesExpansionAction implements GraphAction {
    boolean expanded; // or collapsed
  }

  class LongEdgesAction implements GraphAction {
    boolean show;
  }

  interface GraphChangeEvent {
  }

}
