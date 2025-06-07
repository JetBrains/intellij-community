// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.collapsing;

import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.LiteLinearGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FragmentGenerator {

  public static final class GreenFragment {
    private final @Nullable Integer myUpRedNode;
    private final @Nullable Integer myDownRedNode;
    private final @Unmodifiable @NotNull Set<Integer> myMiddleGreenNodes;

    private GreenFragment(@Nullable Integer upRedNode, @Nullable Integer downRedNode, @NotNull @Unmodifiable Set<Integer> middleGreenNodes) {
      myUpRedNode = upRedNode;
      myDownRedNode = downRedNode;
      myMiddleGreenNodes = middleGreenNodes;
    }

    public @Nullable Integer getUpRedNode() {
      return myUpRedNode;
    }

    public @Nullable Integer getDownRedNode() {
      return myDownRedNode;
    }

    public @NotNull @Unmodifiable Set<Integer> getMiddleGreenNodes() {
      return myMiddleGreenNodes;
    }
  }

  private final @NotNull LiteLinearGraph myGraph;
  private final @NotNull Condition<? super Integer> myRedNodes;

  public FragmentGenerator(@NotNull LiteLinearGraph graph, @NotNull Condition<? super Integer> redNodes) {
    myGraph = graph;
    myRedNodes = redNodes;
  }

  public @NotNull Set<Integer> getMiddleNodes(final int upNode, final int downNode, boolean strict) {
    Set<Integer> downWalk = getWalkNodes(upNode, false, integer -> integer > downNode);
    Set<Integer> upWalk = getWalkNodes(downNode, true, integer -> integer < upNode);

    downWalk.retainAll(upWalk);
    if (strict) {
      downWalk.remove(upNode);
      downWalk.remove(downNode);
    }
    return downWalk;
  }

  public @Nullable Integer getNearRedNode(int startNode, int maxWalkSize, boolean isUp) {
    if (myRedNodes.value(startNode)) return startNode;

    TreeSetNodeIterator walker = new TreeSetNodeIterator(startNode, isUp);
    while (walker.notEmpty()) {
      Integer next = walker.pop();

      if (myRedNodes.value(next)) return next;

      if (maxWalkSize < 0) return null;
      maxWalkSize--;

      walker.addAll(getNodes(next, isUp));
    }

    return null;
  }

  public @NotNull GreenFragment getGreenFragmentForCollapse(int startNode, int maxWalkSize) {
    if (myRedNodes.value(startNode)) return new GreenFragment(null, null, Collections.emptySet());
    Integer upRedNode = getNearRedNode(startNode, maxWalkSize, true);
    Integer downRedNode = getNearRedNode(startNode, maxWalkSize, false);

    Set<Integer> upPart =
      upRedNode != null ? getMiddleNodes(upRedNode, startNode, false) : getWalkNodes(startNode, true, createStopFunction(maxWalkSize));

    Set<Integer> downPart =
      downRedNode != null ? getMiddleNodes(startNode, downRedNode, false) : getWalkNodes(startNode, false, createStopFunction(maxWalkSize));

    Set<Integer> middleNodes = ContainerUtil.map2SetNotNull(ContainerUtil.union(upPart, downPart), i -> i.equals(upRedNode) ||
                                                                                                        i.equals(downRedNode) ? null : i);
    return new GreenFragment(upRedNode, downRedNode, middleNodes);
  }

  private @NotNull Set<Integer> getWalkNodes(int startNode, boolean isUp, Condition<? super Integer> stopFunction) {
    Set<Integer> walkNodes = new HashSet<>();

    TreeSetNodeIterator walker = new TreeSetNodeIterator(startNode, isUp);
    while (walker.notEmpty()) {
      Integer next = walker.pop();
      if (!stopFunction.value(next)) {
        walkNodes.add(next);
        walker.addAll(getNodes(next, isUp));
      }
    }

    return walkNodes;
  }

  private @NotNull List<Integer> getNodes(int nodeIndex, boolean isUp) {
    return myGraph.getNodes(nodeIndex, LiteLinearGraph.NodeFilter.filter(isUp));
  }

  private static @NotNull Condition<Integer> createStopFunction(final int maxNodeCount) {
    return new Condition<>() {
      private int count = maxNodeCount;

      @Override
      public boolean value(Integer integer) {
        count--;
        return count < 0;
      }
    };
  }
}
