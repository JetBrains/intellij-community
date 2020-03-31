// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.impl.permanent;

import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.utils.IntList;
import com.intellij.vcs.log.graph.utils.impl.CompressedIntList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class GraphLayoutImpl implements GraphLayout {
  @NotNull private final IntList myLayoutIndex;

  @NotNull private final List<Integer> myHeadNodeIndex;
  private final int @NotNull [] myStartLayoutIndexForHead;

  public GraphLayoutImpl(int @NotNull [] layoutIndex, @NotNull List<Integer> headNodeIndex, int @NotNull [] startLayoutIndexForHead) {
    myLayoutIndex = CompressedIntList.newInstance(layoutIndex);
    myHeadNodeIndex = headNodeIndex;
    myStartLayoutIndexForHead = startLayoutIndexForHead;
  }

  @Override
  public int getLayoutIndex(int nodeIndex) {
    return myLayoutIndex.get(nodeIndex);
  }

  @Override
  public int getOneOfHeadNodeIndex(int nodeIndex) {
    return getHeadNodeIndex(getLayoutIndex(nodeIndex));
  }

  public int getHeadNodeIndex(int layoutIndex) {
    return myHeadNodeIndex.get(getHeadOrder(layoutIndex));
  }

  @Override
  @NotNull
  public List<Integer> getHeadNodeIndex() {
    return myHeadNodeIndex;
  }

  private int getHeadOrder(int layoutIndex) {
    int i = Arrays.binarySearch(myStartLayoutIndexForHead, layoutIndex);
    return i < 0 ? Math.max(0, -i - 2) : i;
  }
}
