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
package com.intellij.vcs.log.newgraph.render.cell;

import com.intellij.vcs.log.newgraph.gpaph.MutableGraph;
import com.intellij.vcs.log.newgraph.render.GraphCellGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public abstract class AbstractGraphCellGenerator implements GraphCellGenerator {
  @NotNull
  protected final MutableGraph myGraph;

  protected AbstractGraphCellGenerator(@NotNull MutableGraph graph) {
    myGraph = graph;
  }

  public int getCountVisibleRow() {
    return myGraph.getCountVisibleNodes();
  }

  public GraphCell getGraphCell(int visibleRowIndex) {
    List<ShortEdge> downShortEdges;
    List<ShortEdge> upShortEdges;
    if (visibleRowIndex == getCountVisibleRow() - 1)
      downShortEdges = Collections.emptyList();
    else
      downShortEdges = getDownShortEdges(visibleRowIndex);

    if (visibleRowIndex == 0)
      upShortEdges = Collections.emptyList();
    else
      upShortEdges = getDownShortEdges(visibleRowIndex - 1);

    return new GraphCell(getCountVisibleElements(visibleRowIndex),
                         getSpecialElements(visibleRowIndex),
                         upShortEdges,
                         downShortEdges);
  }

  protected abstract int getCountVisibleElements(int visibleRowIndex);

  // visibleRowIndex in [0, getCountVisibleRow() - 2]
  @NotNull
  protected abstract List<ShortEdge> getDownShortEdges(int visibleRowIndex);

  @NotNull
  protected abstract List<SpecialRowElement> getSpecialElements(int visibleRowIndex);
}
