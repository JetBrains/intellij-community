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

package com.intellij.vcs.log.newgraph.gpaph.impl;

import com.intellij.util.containers.HashSet;
import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.newgraph.SomeGraph;
import com.intellij.vcs.log.newgraph.gpaph.Edge;
import com.intellij.vcs.log.newgraph.gpaph.GraphElement;
import com.intellij.vcs.log.newgraph.gpaph.MutableGraph;
import com.intellij.vcs.log.newgraph.gpaph.Node;
import com.intellij.vcs.log.newgraph.gpaph.actions.InternalGraphAction;
import com.intellij.vcs.log.newgraph.gpaph.actions.MouseOverGraphElementInternalGraphAction;
import com.intellij.vcs.log.newgraph.gpaph.actions.RowClickInternalGraphAction;
import com.intellij.vcs.log.newgraph.utils.DfsUtil;
import com.intellij.vcs.log.newgraph.utils.Flags;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.vcs.log.newgraph.utils.MyUtils.setAllValues;

public class ThickHoverControllerImpl extends AbstractThickHoverController {
  @NotNull
  private final PermanentGraph myPermanentGraph;

  @NotNull
  private final MutableGraph myMutableGraph;

  @NotNull
  private final Set<Integer> hoverNodes = new HashSet<Integer>(); // contains visibleIndex

  @NotNull
  private final Flags thickFlags;

  @NotNull
  private final DfsUtil myDfsUtil;

  public ThickHoverControllerImpl(@NotNull PermanentGraph permanentGraph,
                                  @NotNull MutableGraph mutableGraph,
                                  @NotNull Flags thickFlags,
                                  @NotNull DfsUtil dfsUtil) {
    myPermanentGraph = permanentGraph;
    myMutableGraph = mutableGraph;
    this.thickFlags = thickFlags;
    myDfsUtil = dfsUtil;
  }

  @Override
  public boolean isThick(@NotNull GraphElement element) {
    return isOn(element, thickFlags);
  }

  @Override
  public boolean isHover(@NotNull GraphElement element) {
    return false;
  }

  private boolean isOn(@NotNull GraphElement element, @NotNull Flags flags) {
    if (element instanceof Node)
      return flags.get(myMutableGraph.getIndexInPermanentGraph(((Node)element).getVisibleNodeIndex()));

    if (element instanceof Edge) {
      Edge edge = (Edge) element;
      int realUpNodeIndex = myMutableGraph.getIndexInPermanentGraph(edge.getUpNodeVisibleIndex());
      int realDownNodeIndex = myMutableGraph.getIndexInPermanentGraph(edge.getDownNodeVisibleIndex());

      if (realDownNodeIndex == SomeGraph.NOT_LOAD_COMMIT)
        return flags.get(realUpNodeIndex);
      else
        return flags.get(realUpNodeIndex) && flags.get(realDownNodeIndex);
    }

    return false;
  }

  @Override
  public void performAction(@NotNull InternalGraphAction action) {
    super.performAction(action);
    if (action instanceof RowClickInternalGraphAction) {
      setAllValues(thickFlags, false);
      Integer visibleNodeIndex = ((RowClickInternalGraphAction)action).getInfo();
      if (visibleNodeIndex != null) {
        int realRowIndex = myMutableGraph.getIndexInPermanentGraph(visibleNodeIndex);
        enableAllRelativeNodes(thickFlags, realRowIndex);
      }
    }

    if (action instanceof MouseOverGraphElementInternalGraphAction) {
      hoverNodes.clear();
      GraphElement graphElement = ((MouseOverGraphElementInternalGraphAction)action).getInfo();
      if (graphElement != null)
        hoverFragment(graphElement);
    }
  }

  private void enableAllRelativeNodes(@NotNull final Flags flags, int rowIndex) {
    flags.set(rowIndex, true);
    myDfsUtil.nodeDfsIterator(rowIndex, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
        for (int downNode : myPermanentGraph.getDownNodes(currentNode)) {
          if (downNode != SomeGraph.NOT_LOAD_COMMIT && !flags.get(downNode)) {
            flags.set(downNode, true);
            return downNode;
          }
        }
        return DfsUtil.NextNode.NODE_NOT_FOUND;
      }
    });

    myDfsUtil.nodeDfsIterator(rowIndex, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
        for (int upNode : myPermanentGraph.getUpNodes(currentNode)) {
          if (!flags.get(upNode)) {
            flags.set(upNode, true);
            return upNode;
          }
        }
        return DfsUtil.NextNode.NODE_NOT_FOUND;
      }
    });
  }

  private void hoverFragment(@NotNull GraphElement graphElement) {

  }
}
