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
package com.intellij.vcs.log.graph.linearBek;

import com.intellij.util.Function;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.api.printer.PrintElementWithGraphElement;
import com.intellij.vcs.log.graph.impl.facade.BekBaseLinearGraphController;
import com.intellij.vcs.log.graph.impl.facade.CascadeLinearGraphController;
import com.intellij.vcs.log.graph.impl.facade.bek.BekIntMap;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LinearBekController extends CascadeLinearGraphController {
  @NotNull private final LinearGraph myCompiledGraph;

  public LinearBekController(@NotNull BekBaseLinearGraphController controller,
                             @NotNull PermanentGraphInfo permanentGraphInfo,
                             @NotNull final TimestampGetter timestampGetter) {
    super(controller, permanentGraphInfo);
    final BekIntMap bekIntMap = controller.getBekIntMap();
    myCompiledGraph = compileGraph(getDelegateLinearGraphController().getCompiledGraph(),
                                   new BekGraphLayout(permanentGraphInfo.getPermanentGraphLayout(), bekIntMap),
                                   new BekTimestampGetter(timestampGetter, bekIntMap));
  }

  static LinearGraph compileGraph(@NotNull LinearGraph graph, @NotNull GraphLayout graphLayout, @NotNull TimestampGetter timestampGetter) {
    long start = System.currentTimeMillis();
    LinearBekGraph result = new LinearBekGraphBuilder(graph, graphLayout, timestampGetter).build();
    long end = System.currentTimeMillis();
    System.err.println(((double)end - start) / 1000);
    return result;
  }

  @Override
  protected boolean elementIsSelected(@NotNull PrintElementWithGraphElement printElement) {
    return false;
  }

  @NotNull
  @Override
  protected LinearGraphAnswer performDelegateUpdate(@NotNull LinearGraphAnswer delegateAnswer) {
    return delegateAnswer;
  }

  @Nullable
  @Override
  protected LinearGraphAnswer performAction(@NotNull LinearGraphAction action) {
    return null;
  }

  @NotNull
  @Override
  public LinearGraph getCompiledGraph() {
    return myCompiledGraph;
  }

  private static class BekGraphLayout implements GraphLayout {
    private final GraphLayout myGraphLayout;
    private final BekIntMap myBekIntMap;

    public BekGraphLayout(GraphLayout graphLayout, BekIntMap bekIntMap) {
      myGraphLayout = graphLayout;
      myBekIntMap = bekIntMap;
    }

    @Override
    public int getLayoutIndex(int nodeIndex) {
      return myGraphLayout.getLayoutIndex(myBekIntMap.getUsualIndex(nodeIndex));
    }

    @Override
    public int getOneOfHeadNodeIndex(int nodeIndex) {
      int usualIndex = myGraphLayout.getOneOfHeadNodeIndex(myBekIntMap.getUsualIndex(nodeIndex));
      return myBekIntMap.getBekIndex(usualIndex);
    }

    @NotNull
    @Override
    public List<Integer> getHeadNodeIndex() {
      List<Integer> bekIndexes = new ArrayList<Integer>();
      for (int head : myGraphLayout.getHeadNodeIndex()) {
        bekIndexes.add(myBekIntMap.getBekIndex(head));
      }
      return bekIndexes;
    }
  }

  private static class BekTimestampGetter implements TimestampGetter {
    private final TimestampGetter myTimestampGetter;
    private final BekIntMap myBekIntMap;

    public BekTimestampGetter(TimestampGetter timestampGetter, BekIntMap bekIntMap) {
      myTimestampGetter = timestampGetter;
      myBekIntMap = bekIntMap;
    }

    @Override
    public int size() {
      return myTimestampGetter.size();
    }

    @Override
    public long getTimestamp(int index) {
      return myTimestampGetter.getTimestamp(myBekIntMap.getUsualIndex(index));
    }
  }
}
