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

import com.intellij.openapi.util.Pair;
import com.intellij.util.NullableFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.SLRUMap;
import com.intellij.vcs.log.graph.SimplePrintElement;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.printer.PrintElementManager;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.*;

public class PrintElementGeneratorImpl extends AbstractPrintElementGenerator {
  public static final int LONG_EDGE_SIZE = 30;
  private static final int LONG_EDGE_PART_SIZE = 1;

  private static final int VERY_LONG_EDGE_SIZE = 1000;
  private static final int VERY_LONG_EDGE_PART_SIZE = 250;
  private static final int CACHE_SIZE = 100;
  private static final boolean SHOW_ARROW_WHEN_SHOW_LONG_EDGES = true;


  @NotNull private final SLRUMap<Integer, List<GraphElement>> cache = new SLRUMap<Integer, List<GraphElement>>(CACHE_SIZE, CACHE_SIZE * 2);
  @NotNull private final EdgesInRowGenerator myEdgesInRowGenerator;
  @NotNull private final Comparator<GraphElement> myGraphElementComparator;

  private final int myLongSize;
  private final int myShowingPartSize;
  private final int myAddNearArrowSize;

  public PrintElementGeneratorImpl(@NotNull LinearGraph graph, @NotNull PrintElementManager printElementManager, boolean showLongEdges) {
    super(graph, printElementManager);
    myEdgesInRowGenerator = new EdgesInRowGenerator(graph);
    myGraphElementComparator = printElementManager.getGraphElementComparator();
    if (showLongEdges) {
      myLongSize = VERY_LONG_EDGE_SIZE;
      myShowingPartSize = VERY_LONG_EDGE_PART_SIZE;
      if (SHOW_ARROW_WHEN_SHOW_LONG_EDGES) {
        myAddNearArrowSize = LONG_EDGE_SIZE;
      }
      else {
        myAddNearArrowSize = Integer.MAX_VALUE;
      }
    }
    else {
      myLongSize = LONG_EDGE_SIZE;
      myShowingPartSize = LONG_EDGE_PART_SIZE;
      myAddNearArrowSize = Integer.MAX_VALUE;
    }
  }

  @TestOnly
  public PrintElementGeneratorImpl(@NotNull LinearGraph graph,
                                   @NotNull PrintElementManager printElementManager,
                                   int longSize,
                                   int showingPartSize,
                                   int addNearArrowSize) {
    super(graph, printElementManager);
    myEdgesInRowGenerator = new EdgesInRowGenerator(graph);
    myGraphElementComparator = printElementManager.getGraphElementComparator();
    myLongSize = longSize;
    myShowingPartSize = showingPartSize;
    myAddNearArrowSize = addNearArrowSize;
  }

  @NotNull
  @Override
  protected List<ShortEdge> getDownShortEdges(int rowIndex) {
    NullableFunction<GraphEdge, Integer> endPosition = createEndPositionFunction(rowIndex);

    List<ShortEdge> result = new ArrayList<ShortEdge>();
    List<GraphElement> visibleElements = getSortedVisibleElementsInRow(rowIndex);

    for (int startPosition = 0; startPosition < visibleElements.size(); startPosition++) {
      GraphElement element = visibleElements.get(startPosition);
      if (element instanceof GraphNode) {
        int nodeIndex = ((GraphNode)element).getNodeIndex();
        for (GraphEdge edge : myLinearGraph.getAdjacentEdges(nodeIndex, EdgeFilter.ALL)) {
          if (isEdgeDown(edge, nodeIndex)) {
            Integer endPos = endPosition.fun(edge);
            if (endPos != null) result.add(new ShortEdge(edge, startPosition, endPos));
          }
        }
      }

      if (element instanceof GraphEdge) {
        GraphEdge edge = (GraphEdge)element;
        Integer endPos = endPosition.fun(edge);
        if (endPos != null) result.add(new ShortEdge(edge, startPosition, endPos));
      }
    }

    return result;
  }

  @NotNull
  private NullableFunction<GraphEdge, Integer> createEndPositionFunction(int visibleRowIndex) {
    List<GraphElement> visibleElementsInNextRow = getSortedVisibleElementsInRow(visibleRowIndex + 1);

    final Map<GraphElement, Integer> toPosition = new HashMap<GraphElement, Integer>();
    for (int position = 0; position < visibleElementsInNextRow.size(); position++) {
      toPosition.put(visibleElementsInNextRow.get(position), position);
    }

    return new NullableFunction<GraphEdge, Integer>() {
      @Override
      @Nullable
      public Integer fun(GraphEdge edge) {
        Integer position = toPosition.get(edge);
        if (position == null) {
          Integer downNodeIndex = edge.getDownNodeIndex();
          if (downNodeIndex != null) position = toPosition.get(myLinearGraph.getGraphNode(downNodeIndex));
        }
        return position;
      }
    };
  }

  @NotNull
  @Override
  protected List<SimpleRowElement> getSimpleRowElements(int visibleRowIndex) {
    List<SimpleRowElement> result = new SmartList<SimpleRowElement>();
    List<GraphElement> sortedVisibleElementsInRow = getSortedVisibleElementsInRow(visibleRowIndex);

    for (int position = 0; position < sortedVisibleElementsInRow.size(); position++) {
      GraphElement element = sortedVisibleElementsInRow.get(position);
      if (element instanceof GraphNode) {
        result.add(new SimpleRowElement(element, SimplePrintElement.Type.NODE, position));
      }

      if (element instanceof GraphEdge) {
        GraphEdge edge = (GraphEdge)element;
        Pair<Integer, Integer> normalEdge = asNormalEdge(edge);
        if (normalEdge != null) {
          int edgeSize = normalEdge.second - normalEdge.first;
          int upOffset = visibleRowIndex - normalEdge.first;
          int downOffset = normalEdge.second - visibleRowIndex;

          if (edgeSize >= myLongSize) addArrowIfNeeded(result, edge, position, upOffset, downOffset, myShowingPartSize);

          if (edgeSize >= myAddNearArrowSize) addArrowIfNeeded(result, edge, position, upOffset, downOffset, 1);

        }
        else { // special edges
          switch (edge.getType()) {
            case DOTTED_ARROW_DOWN:
            case NOT_LOAD_COMMIT:
              if (intEqual(edge.getUpNodeIndex(), visibleRowIndex - 1)) {
                result.add(new SimpleRowElement(edge, SimplePrintElement.Type.DOWN_ARROW, position));
              }
              break;
            case DOTTED_ARROW_UP:
              if (intEqual(edge.getDownNodeIndex(), visibleRowIndex + 1)) // todo case 0-row arrow
              {
                result.add(new SimpleRowElement(edge, SimplePrintElement.Type.UP_ARROW, position));
              }
              break;
            default:
              // todo log some error (nothing here)
          }
        }
      }
    }
    return result;
  }

  private static void addArrowIfNeeded(@NotNull List<SimpleRowElement> result,
                                       @NotNull GraphEdge edge,
                                       int position,
                                       int upOffset,
                                       int downOffset,
                                       int showingPartSize) {
    if (upOffset == showingPartSize) result.add(new SimpleRowElement(edge, SimplePrintElement.Type.DOWN_ARROW, position));

    if (downOffset == showingPartSize) result.add(new SimpleRowElement(edge, SimplePrintElement.Type.UP_ARROW, position));
  }

  private boolean edgeIsVisibleInRow(@NotNull GraphEdge edge, int visibleRowIndex) {
    Pair<Integer, Integer> normalEdge = LinearGraphUtils.asNormalEdge(edge);
    if (normalEdge == null) // e.d. edge is special. See addSpecialEdges
    {
      return false;
    }
    if (normalEdge.second - normalEdge.first < myLongSize) {
      return true;
    }
    else {
      return visibleRowIndex - normalEdge.first <= myShowingPartSize || normalEdge.second - visibleRowIndex <= myShowingPartSize;
    }
  }

  private void addSpecialEdges(@NotNull List<GraphElement> result, int rowIndex) {
    if (rowIndex > 0) {
      for (GraphEdge edge : myLinearGraph.getAdjacentEdges(rowIndex - 1, EdgeFilter.SPECIAL)) {
        assert !edge.getType().isNormalEdge();
        if (isEdgeDown(edge, rowIndex - 1)) result.add(edge);
      }
    }
    if (rowIndex < myLinearGraph.nodesCount() - 1) {
      for (GraphEdge edge : myLinearGraph.getAdjacentEdges(rowIndex + 1, EdgeFilter.SPECIAL)) {
        assert !edge.getType().isNormalEdge();
        if (isEdgeUp(edge, rowIndex + 1)) result.add(edge);
      }
    }
  }

  @NotNull
  private List<GraphElement> getSortedVisibleElementsInRow(int rowIndex) {
    List<GraphElement> graphElements = cache.get(rowIndex);
    if (graphElements != null) {
      return graphElements;
    }

    List<GraphElement> result = new ArrayList<GraphElement>();
    result.add(myLinearGraph.getGraphNode(rowIndex));

    for (GraphEdge edge : myEdgesInRowGenerator.getEdgesInRow(rowIndex)) {
      if (edgeIsVisibleInRow(edge, rowIndex)) result.add(edge);
    }

    addSpecialEdges(result, rowIndex);

    Collections.sort(result, myGraphElementComparator);
    cache.put(rowIndex, result);
    return result;
  }

}
