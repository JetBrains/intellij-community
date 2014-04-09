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
package com.intellij.vcs.log.graph.impl.print;

import com.intellij.vcs.log.graph.EdgePrintElement;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.graph.SimplePrintElement;
import com.intellij.vcs.log.graph.api.PrintedLinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.printer.PrintElementGenerator;
import com.intellij.vcs.log.graph.api.printer.PrintElementsManager;
import com.intellij.vcs.log.graph.impl.print.elements.EdgePrintElementImpl;
import com.intellij.vcs.log.graph.impl.print.elements.PrintElementWithGraphElement;
import com.intellij.vcs.log.graph.impl.print.elements.SimplePrintElementImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public abstract class AbstractPrintElementGenerator implements PrintElementGenerator {

  @NotNull
  protected final PrintedLinearGraph myPrintedLinearGraph;
  @NotNull
  protected final PrintElementsManager myPrintElementsManager;

  protected AbstractPrintElementGenerator(@NotNull PrintedLinearGraph printedLinearGraph,
                                          @NotNull PrintElementsManager printElementsManager) {
    myPrintedLinearGraph = printedLinearGraph;
    myPrintElementsManager = printElementsManager;
  }

  @NotNull
  public Collection<PrintElement> getPrintElements(int rowIndex) {
    Collection<PrintElement> result = new ArrayList<PrintElement>();

    if (rowIndex < myPrintedLinearGraph.nodesCount() - 1) {
      for (ShortEdge shortEdge : getDownShortEdges(rowIndex)) {
        result.add(new EdgePrintElementImpl(rowIndex, shortEdge.myUpPosition, shortEdge.myDownPosition, EdgePrintElement.Type.DOWN, shortEdge.myEdge,
                                            myPrintElementsManager));
      }
    }

    if (rowIndex > 0) {
      for (ShortEdge shortEdge : getDownShortEdges(rowIndex - 1)) {
        result.add(new EdgePrintElementImpl(rowIndex, shortEdge.myDownPosition, shortEdge.myUpPosition, EdgePrintElement.Type.UP, shortEdge.myEdge,
                                            myPrintElementsManager));
      }
    }

    for (SimpleRowElement rowElement : getSimpleRowElements(rowIndex)) {
      result.add(new SimplePrintElementImpl(rowIndex, rowElement.myPosition, rowElement.myType, rowElement.myElement,
                                            myPrintElementsManager));
    }

    return result;
  }

  @NotNull
  @Override
  public GraphElement getRelatedGraphElement(@NotNull PrintElement printElement) {
    if (printElement instanceof PrintElementWithGraphElement) {
      return ((PrintElementWithGraphElement)printElement).getGraphElement();
    }

    if (printElement instanceof SimplePrintElement) {
      for (SimpleRowElement rowElement : getSimpleRowElements(printElement.getRowIndex())) {
        if (rowElement.myPosition == printElement.getPositionInCurrentRow())
          return rowElement.myElement;
      }
    }

    if (printElement instanceof EdgePrintElement) {
      EdgePrintElement edgePrintElement = (EdgePrintElement)printElement;
      if (edgePrintElement.getType() == EdgePrintElement.Type.DOWN) {
        for (ShortEdge shortEdge : getDownShortEdges(printElement.getRowIndex())) {
          if (shortEdge.myUpPosition == edgePrintElement.getPositionInCurrentRow() &&
            shortEdge.myDownPosition == edgePrintElement.getPositionInOtherRow()) {
            return shortEdge.myEdge;
          }
        }
      }

      if (edgePrintElement.getType() == EdgePrintElement.Type.DOWN) {
        for (ShortEdge shortEdge : getDownShortEdges(printElement.getRowIndex() - 1)) {
          if (shortEdge.myDownPosition == edgePrintElement.getPositionInCurrentRow() &&
              shortEdge.myUpPosition == edgePrintElement.getPositionInOtherRow()) {
            return shortEdge.myEdge;
          }
        }
      }
    }
    throw new IllegalStateException("Not found graphElement for this printElement: " + printElement);
  }

  // rowIndex in [0, getCountVisibleRow() - 2]
  @NotNull
  protected abstract Collection<ShortEdge> getDownShortEdges(int rowIndex);

  @NotNull
  protected abstract Collection<SimpleRowElement> getSimpleRowElements(int rowIndex);

  protected static class ShortEdge {
    @NotNull
    public final GraphEdge myEdge;
    public final int myUpPosition;
    public final int myDownPosition;

    public ShortEdge(@NotNull GraphEdge edge, int upPosition, int downPosition) {
      myEdge = edge;
      myUpPosition = upPosition;
      myDownPosition = downPosition;
    }
  }

  protected static class SimpleRowElement {
    @NotNull
    public final GraphElement myElement;
    @NotNull
    public final SimplePrintElement.Type myType;
    public final int myPosition;

    public SimpleRowElement(@NotNull GraphElement element, @NotNull SimplePrintElement.Type type, int position) {
      myElement = element;
      myPosition = position;
      myType = type;
    }
  }
}
