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

import com.intellij.vcs.log.newgraph.gpaph.Node;
import com.intellij.vcs.log.newgraph.gpaph.impl.FilterMutableGraph;
import com.intellij.vcs.log.newgraph.render.GraphCellGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FilterGraphCellGenerator implements GraphCellGenerator {

  @NotNull
  private final GraphCellGenerator myDelegateCellGenerator;

  @NotNull
  private final FilterMutableGraph myFilterMutableGraph;


  public FilterGraphCellGenerator(@NotNull FilterMutableGraph filterMutableGraph) {
    myFilterMutableGraph = filterMutableGraph;
    myDelegateCellGenerator = new GraphCellGeneratorImpl(filterMutableGraph);
  }

  @Override
  public int getCountVisibleRow() {
    return myDelegateCellGenerator.getCountVisibleRow();
  }

  @Override
  public GraphCell getGraphCell(int visibleRowIndex) {
    GraphCell delegateCell = myDelegateCellGenerator.getGraphCell(visibleRowIndex);
    List<SpecialRowElement> newSpecialElements = new ArrayList<SpecialRowElement>();
    newSpecialElements.addAll(delegateCell.getSpecialRowElements());

    Node node = null;
    for (SpecialRowElement element : newSpecialElements) {
      if (element.getType() == SpecialRowElement.Type.NODE)
        node = (Node)element.getElement();
    }
    assert node != null;

    if (myFilterMutableGraph.nextRowIsHide(visibleRowIndex))
      newSpecialElements.add(new SpecialRowElement(node, 0, SpecialRowElement.Type.DOWN_HARMONICA));

    return new GraphCell(delegateCell.getCountElements(), newSpecialElements, delegateCell.getUpEdges(), delegateCell.getDownEdges());
  }

  @Override
  public boolean isShowLongEdges() {
    return myDelegateCellGenerator.isShowLongEdges();
  }

  @Override
  public void setShowLongEdges(boolean showLongEdges) {
    myDelegateCellGenerator.setShowLongEdges(showLongEdges);
  }

  @Override
  public void invalidate() {
    myDelegateCellGenerator.invalidate();
  }

}
