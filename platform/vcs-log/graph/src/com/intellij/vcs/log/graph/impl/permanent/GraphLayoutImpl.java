// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.impl.permanent;

import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.utils.IntList;
import com.intellij.vcs.log.graph.utils.impl.CompressedIntList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GraphLayoutImpl implements GraphLayout {
  @NotNull private final IntList myLayoutIndex;

  @NotNull private final List<Integer> myHeadNodeIndex;
  @NotNull private final int[] myStartLayoutIndexForHead;

  public GraphLayoutImpl(@NotNull int[] layoutIndex, @NotNull List<Integer> headNodeIndex, @NotNull int[] startLayoutIndexForHead) {
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
    int a = 0;
    int b = myStartLayoutIndexForHead.length - 1;
    while (b > a) {
      int middle = (a + b + 1) / 2;
      if (myStartLayoutIndexForHead[middle] <= layoutIndex) {
        a = middle;
      }
      else {
        b = middle - 1;
      }
    }
    return a;
  }
}
