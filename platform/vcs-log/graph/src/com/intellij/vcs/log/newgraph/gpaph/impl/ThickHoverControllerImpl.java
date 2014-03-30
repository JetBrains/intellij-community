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

import com.intellij.util.Function;
import com.intellij.util.containers.HashSet;
import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.newgraph.SomeGraph;
import com.intellij.vcs.log.newgraph.gpaph.Edge;
import com.intellij.vcs.log.newgraph.gpaph.GraphElement;
import com.intellij.vcs.log.newgraph.gpaph.MutableGraph;
import com.intellij.vcs.log.newgraph.gpaph.Node;
import com.intellij.vcs.log.newgraph.gpaph.actions.InternalGraphAction;
import com.intellij.vcs.log.newgraph.gpaph.actions.MouseOverGraphElementInternalGraphAction;
import com.intellij.vcs.log.newgraph.gpaph.actions.SelectAllRelativeCommitsInternalGraphAction;
import com.intellij.vcs.log.newgraph.gpaph.fragments.FragmentGenerator;
import com.intellij.vcs.log.newgraph.gpaph.fragments.GraphFragment;
import com.intellij.vcs.log.newgraph.utils.DfsUtil;
import com.intellij.vcs.log.facade.utils.Flags;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.vcs.log.newgraph.utils.MyUtils.setAllValues;

public class ThickHoverControllerImpl extends AbstractThickHoverController {
  @NotNull
  private final PermanentGraph myPermanentGraph;

  @NotNull
  private final MutableGraph myMutableGraph;

  @NotNull
  private final FragmentGenerator myFragmentGenerator;

  @NotNull
  private final Set<Integer> hoverNodes = new HashSet<Integer>(); // contains visibleNodeIndex

  @NotNull
  private final Flags myThickFlags;

  @NotNull
  private final DfsUtil myDfsUtil;

  public ThickHoverControllerImpl(@NotNull PermanentGraph permanentGraph,
                                  @NotNull MutableGraph mutableGraph,
                                  @NotNull FragmentGenerator fragmentGenerator,
                                  @NotNull Flags thickFlags,
                                  @NotNull DfsUtil dfsUtil) {
    myPermanentGraph = permanentGraph;
    myMutableGraph = mutableGraph;
    myFragmentGenerator = fragmentGenerator;
    myThickFlags = thickFlags;
    myDfsUtil = dfsUtil;
  }

  @Override
  public boolean isThick(@NotNull GraphElement element) {
    return isOn(element, new Function<Integer, Boolean>() {
      @Override
      public Boolean fun(Integer integer) {
        return myThickFlags.get(myMutableGraph.getIndexInPermanentGraph(integer));
      }
    });
  }

  @Override
  public boolean isHover(@NotNull GraphElement element) {
    return isOn(element, new Function<Integer, Boolean>() {
      @Override
      public Boolean fun(Integer integer) {
        return hoverNodes.contains(integer);
      }
    });
  }

  private static boolean isOn(@NotNull GraphElement element, @NotNull Function<Integer, Boolean> visibleNodeIndexToOn) {
    if (element instanceof Node)
      return visibleNodeIndexToOn.fun(((Node)element).getVisibleNodeIndex());

    if (element instanceof Edge) {
      Edge edge = (Edge) element;
      int visibleUpNodeIndex = edge.getUpNodeVisibleIndex();
      int visibleDownNodeIndex = edge.getDownNodeVisibleIndex();

      if (visibleDownNodeIndex == SomeGraph.NOT_LOAD_COMMIT)
        return false;
      else
        return visibleNodeIndexToOn.fun(visibleUpNodeIndex) && visibleNodeIndexToOn.fun(visibleDownNodeIndex);
    }

    return false;
  }

  @Override
  public void performAction(@NotNull InternalGraphAction action) {
    super.performAction(action);
    if (action instanceof SelectAllRelativeCommitsInternalGraphAction) {
      setAllValues(myThickFlags, false);
      Integer visibleNodeIndex = ((SelectAllRelativeCommitsInternalGraphAction)action).getInfo();
      if (visibleNodeIndex != null) {
        int realRowIndex = myMutableGraph.getIndexInPermanentGraph(visibleNodeIndex);
        enableAllRelativeNodes(myThickFlags, realRowIndex);
      }
    }

    if (action instanceof MouseOverGraphElementInternalGraphAction) {
      hoverNodes.clear();
      GraphElement graphElement = ((MouseOverGraphElementInternalGraphAction)action).getInfo();
      if (graphElement != null)
        hoverFragment(graphElement);
    }
  }

  private void enableAllRelativeNodes(@NotNull final Flags flags, final int rowIndex) {
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
    Edge collapsedEdge = MutableGraphWithHiddenNodes.containedCollapsedEdge(graphElement);
    if (collapsedEdge != null) {
      hoverNodes.add(collapsedEdge.getUpNodeVisibleIndex());
      hoverNodes.add(collapsedEdge.getDownNodeVisibleIndex());
      return;
    }

    GraphFragment fragment = myFragmentGenerator.getPartLongFragment(graphElement);
    if (fragment == null)
      return;

    hoverNodes.add(fragment.upVisibleNodeIndex);
    hoverNodes.add(fragment.downVisibleNodeIndex);

    myDfsUtil.nodeDfsIterator(fragment.upVisibleNodeIndex, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
        for (Edge downEdge : myMutableGraph.getNode(currentNode).getDownEdges()) {
          int downNode = downEdge.getDownNodeVisibleIndex();
          if (hoverNodes.add(downNode))
            return downNode;
        }
        return DfsUtil.NextNode.NODE_NOT_FOUND;
      }
    });

  }
}
