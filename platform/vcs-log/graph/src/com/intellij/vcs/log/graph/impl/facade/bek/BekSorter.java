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
package com.intellij.vcs.log.graph.impl.facade.bek;

import com.intellij.openapi.util.Pair;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutImpl;
import com.intellij.vcs.log.graph.utils.IntList;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import com.intellij.vcs.log.graph.utils.impl.CompressedIntList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BekSorter {
  @NotNull
  public static BekIntMap createBekMap(@NotNull LinearGraph permanentGraph,
                                       @NotNull GraphLayoutImpl graphLayout,
                                       @NotNull TimestampGetter timestampGetter) {
    BekSorter bekSorter = new BekSorter(permanentGraph, graphLayout, timestampGetter);

    List<Integer> result = bekSorter.getResult();
    assert result.size() == permanentGraph.nodesCount();
    return createBekIntMap(result);
  }

  private static BekIntMap createBekIntMap(final List<Integer> result) {

    final int[] reverseMap = new int[result.size()];
    for (int i = 0; i < result.size(); i++) {
      reverseMap[result.get(i)] = i;
    }

    final IntList compressedBekMap = CompressedIntList.newInstance(new IntList() {
      @Override
      public int size() {
        return result.size();
      }

      @Override
      public int get(int index) {
        return result.get(index);
      }
    }, CompressedIntList.DEFAULT_BLOCK_SIZE);

    final IntList compressedReverseMap = CompressedIntList.newInstance(reverseMap);
    return new BekIntMap() {
      @Override
      public int size() {
        return compressedBekMap.size();
      }

      @Override
      public int getBekIndex(int usualIndex) {
        return compressedReverseMap.get(usualIndex);
      }

      @Override
      public int getUsualIndex(int bekIndex) {
        return compressedBekMap.get(bekIndex);
      }
    };
  }

  @NotNull private final LinearGraph myPermanentGraph;

  @NotNull private final GraphLayoutImpl myGraphLayout;

  @NotNull private final TimestampGetter myTimestampGetter;

  private BekSorter(@NotNull LinearGraph permanentGraph, @NotNull GraphLayoutImpl graphLayout, @NotNull TimestampGetter timestampGetter) {
    myPermanentGraph = permanentGraph;
    myGraphLayout = graphLayout;
    myTimestampGetter = timestampGetter;
  }

  public List<Integer> getResult() {
    BekBranchCreator bekBranchCreator = new BekBranchCreator(myPermanentGraph, myGraphLayout);
    Pair<List<BekBranch>, BekEdgeRestrictions> branches = bekBranchCreator.getResult();

    BekBranchMerger bekBranchMerger = new BekBranchMerger(branches.first, branches.second, myTimestampGetter);
    return bekBranchMerger.getResult();
  }

}
