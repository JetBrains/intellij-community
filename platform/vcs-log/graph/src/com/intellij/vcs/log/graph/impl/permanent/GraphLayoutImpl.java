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

  GraphLayoutImpl(@NotNull int[] layoutIndex, @NotNull List<Integer> headNodeIndex, @NotNull int[] startLayoutIndexForHead) {
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
