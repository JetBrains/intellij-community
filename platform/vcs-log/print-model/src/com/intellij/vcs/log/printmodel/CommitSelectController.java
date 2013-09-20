/*
 * Copyright 2010-2013 JetBrains s.r.o.
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

package com.intellij.vcs.log.printmodel;

import com.intellij.vcs.log.graph.elements.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class CommitSelectController {
  private final Set<Node> selectedNodes = new HashSet<Node>();

  private Node dragAndDropNode = null;
  private boolean above;


  // node == null - unSelect
  public void selectDragAndDropNode(@Nullable Node node, boolean above) {
    dragAndDropNode = node;
    this.above = above;
  }

  public Node getDragAndDropNode() {
    return dragAndDropNode;
  }

  public boolean isAbove() {
    return above;
  }

  public void select(Set<Node> nodes) {
    selectedNodes.addAll(nodes);
  }

  public void deselectAll() {
    selectedNodes.clear();
  }

  public boolean isSelected(@NotNull Node element) {
    return selectedNodes.contains(element);
  }
}
