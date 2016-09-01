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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.NullableFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SLRUMap;
import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.printer.PrintElementManager;
import com.intellij.vcs.log.graph.utils.NormalEdge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.*;

public class PrintElementGeneratorImpl extends AbstractPrintElementGenerator {
  @NotNull private static final Logger LOG = Logger.getInstance(PrintElementGeneratorImpl.class);

  public static final int LONG_EDGE_SIZE = 30;
  private static final int LONG_EDGE_PART_SIZE = 1;

  private static final int VERY_LONG_EDGE_SIZE = 1000;
  private static final int VERY_LONG_EDGE_PART_SIZE = 250;
  private static final int CACHE_SIZE = 100;
  private static final boolean SHOW_ARROW_WHEN_SHOW_LONG_EDGES = true;
  private static final int SAMPLE_SIZE = 20000;


  @NotNull private final SLRUMap<Integer, List<GraphElement>> myCache = new SLRUMap<>(CACHE_SIZE, CACHE_SIZE * 2);
  @NotNull private final EdgesInRowGenerator myEdgesInRowGenerator;
  @NotNull private final Comparator<GraphElement> myGraphElementComparator;

  private final int myLongEdgeSize;
  private final int myVisiblePartSize;
  private final int myEdgeWithArrowSize;
  private int myRecommendedWidth = 0;

  public PrintElementGeneratorImpl(@NotNull LinearGraph graph, @NotNull PrintElementManager printElementManager, boolean showLongEdges) {
    super(graph, printElementManager);
    myEdgesInRowGenerator = new EdgesInRowGenerator(graph);
    myGraphElementComparator = printElementManager.getGraphElementComparator();
    if (showLongEdges) {
      myLongEdgeSize = VERY_LONG_EDGE_SIZE;
      myVisiblePartSize = VERY_LONG_EDGE_PART_SIZE;
      if (SHOW_ARROW_WHEN_SHOW_LONG_EDGES) {
        myEdgeWithArrowSize = LONG_EDGE_SIZE;
      }
      else {
        myEdgeWithArrowSize = Integer.MAX_VALUE;
      }
    }
    else {
      myLongEdgeSize = LONG_EDGE_SIZE;
      myVisiblePartSize = LONG_EDGE_PART_SIZE;
      myEdgeWithArrowSize = Integer.MAX_VALUE;
    }
  }

  @TestOnly
  public PrintElementGeneratorImpl(@NotNull LinearGraph graph,
                                   @NotNull PrintElementManager printElementManager,
                                   int longEdgeSize,
                                   int visiblePartSize,
                                   int edgeWithArrowSize) {
    super(graph, printElementManager);
    myEdgesInRowGenerator = new EdgesInRowGenerator(graph);
    myGraphElementComparator = printElementManager.getGraphElementComparator();
    myLongEdgeSize = longEdgeSize;
    myVisiblePartSize = visiblePartSize;
    myEdgeWithArrowSize = edgeWithArrowSize;
  }

  public int getRecommendedWidth() {
    if (myRecommendedWidth <= 0) {
      int n = Math.min(SAMPLE_SIZE, myLinearGraph.nodesCount());

      double sum = 0;
      double sumSquares = 0;
      int edgesCount = 0;
      Set<NormalEdge> currentNormalEdges = ContainerUtil.newHashSet();

      for (int i = 0; i < n; i++) {
        List<GraphEdge> adjacentEdges = myLinearGraph.getAdjacentEdges(i, EdgeFilter.ALL);
        int upArrows = 0;
        int downArrows = 0;
        for (GraphEdge e : adjacentEdges) {
          NormalEdge normalEdge = asNormalEdge(e);
          if (normalEdge != null) {
            if (isEdgeUp(e, i)) {
              currentNormalEdges.remove(normalEdge);
            }
            else {
              currentNormalEdges.add(normalEdge);
            }
          }
          else {
            if (e.getType() == GraphEdgeType.DOTTED_ARROW_UP) {
              upArrows++;
            }
            else {
              downArrows++;
            }
          }
        }

        int newEdgesCount = 0;
        for (NormalEdge e : currentNormalEdges) {
          if (isEdgeVisibleInRow(e, i)) {
            newEdgesCount++;
          }
          else {
            RowElementType arrow = getArrowType(e, i);
            if (arrow == RowElementType.DOWN_ARROW) {
              downArrows++;
            }
            else if (arrow == RowElementType.UP_ARROW) {
              upArrows++;
            }
          }
        }

        int width = Math.max(edgesCount + upArrows, newEdgesCount + downArrows);

        sum += width;
        sumSquares += width * width;

        edgesCount = newEdgesCount;
      }

      double average = sum / n;
      double deviation = Math.sqrt(sumSquares / n - average * average);
      myRecommendedWidth = (int)Math.round(average + deviation);
    }

    return myRecommendedWidth;
  }

  @NotNull
  @Override
  protected List<ShortEdge> getDownShortEdges(int rowIndex) {
    NullableFunction<GraphEdge, Integer> endPosition = createEndPositionFunction(rowIndex);

    List<ShortEdge> result = new ArrayList<>();
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

    final Map<GraphElement, Integer> toPosition = new HashMap<>();
    for (int position = 0; position < visibleElementsInNextRow.size(); position++) {
      toPosition.put(visibleElementsInNextRow.get(position), position);
    }

    return edge -> {
      Integer position = toPosition.get(edge);
      if (position == null) {
        Integer downNodeIndex = edge.getDownNodeIndex();
        if (downNodeIndex != null) position = toPosition.get(myLinearGraph.getGraphNode(downNodeIndex));
      }
      return position;
    };
  }

  @NotNull
  @Override
  protected List<SimpleRowElement> getSimpleRowElements(int visibleRowIndex) {
    List<SimpleRowElement> result = new SmartList<>();
    List<GraphElement> sortedVisibleElementsInRow = getSortedVisibleElementsInRow(visibleRowIndex);

    for (int position = 0; position < sortedVisibleElementsInRow.size(); position++) {
      GraphElement element = sortedVisibleElementsInRow.get(position);
      if (element instanceof GraphNode) {
        result.add(new SimpleRowElement(element, RowElementType.NODE, position));
      }

      if (element instanceof GraphEdge) {
        GraphEdge edge = (GraphEdge)element;
        RowElementType arrowType = getArrowType(edge, visibleRowIndex);
        if (arrowType != null) {
          result.add(new SimpleRowElement(edge, arrowType, position));
        }
      }
    }
    return result;
  }

  @Nullable
  private RowElementType getArrowType(@NotNull GraphEdge edge, int rowIndex) {
    NormalEdge normalEdge = asNormalEdge(edge);
    if (normalEdge != null) {
      return getArrowType(normalEdge, rowIndex);
    }
    else { // special edges
      switch (edge.getType()) {
        case DOTTED_ARROW_DOWN:
        case NOT_LOAD_COMMIT:
          if (intEqual(edge.getUpNodeIndex(), rowIndex - 1)) {
            return RowElementType.DOWN_ARROW;
          }
          break;
        case DOTTED_ARROW_UP:
          // todo case 0-row arrow
          if (intEqual(edge.getDownNodeIndex(), rowIndex + 1)) {
            return RowElementType.UP_ARROW;
          }
          break;
        default:
          LOG.error("Unknown special edge type " + edge.getType() + " at row " + rowIndex);
      }
    }
    return null;
  }

  @Nullable
  private RowElementType getArrowType(@NotNull NormalEdge normalEdge, int rowIndex) {
    int edgeSize = normalEdge.down - normalEdge.up;
    int upOffset = rowIndex - normalEdge.up;
    int downOffset = normalEdge.down - rowIndex;

    if (edgeSize >= myLongEdgeSize) {
      if (upOffset == myVisiblePartSize) {
        LOG.assertTrue(downOffset != myVisiblePartSize, "Both up and down arrow at row " +
                                                        rowIndex); // this can not happen due to how constants are picked out, but just in case
        return RowElementType.DOWN_ARROW;
      }
      if (downOffset == myVisiblePartSize) return RowElementType.UP_ARROW;
    }
    if (edgeSize >= myEdgeWithArrowSize) {
      if (upOffset == 1) {
        LOG.assertTrue(downOffset != 1, "Both up and down arrow at row " + rowIndex);
        return RowElementType.DOWN_ARROW;
      }
      if (downOffset == 1) return RowElementType.UP_ARROW;
    }
    return null;
  }

  private boolean isEdgeVisibleInRow(@NotNull GraphEdge edge, int visibleRowIndex) {
    NormalEdge normalEdge = asNormalEdge(edge);
    if (normalEdge == null) {
      // e.d. edge is special. See addSpecialEdges
      return false;
    }
    return isEdgeVisibleInRow(normalEdge, visibleRowIndex);
  }

  private boolean isEdgeVisibleInRow(@NotNull NormalEdge normalEdge, int visibleRowIndex) {
    return normalEdge.down - normalEdge.up < myLongEdgeSize || getAttachmentDistance(normalEdge, visibleRowIndex) <= myVisiblePartSize;
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
    List<GraphElement> graphElements = myCache.get(rowIndex);
    if (graphElements != null) {
      return graphElements;
    }

    List<GraphElement> result = new ArrayList<>();
    result.add(myLinearGraph.getGraphNode(rowIndex));

    for (GraphEdge edge : myEdgesInRowGenerator.getEdgesInRow(rowIndex)) {
      if (isEdgeVisibleInRow(edge, rowIndex)) result.add(edge);
    }

    addSpecialEdges(result, rowIndex);

    Collections.sort(result, myGraphElementComparator);
    myCache.put(rowIndex, result);
    return result;
  }

  private static int getAttachmentDistance(@NotNull NormalEdge e1, int rowIndex) {
    return Math.min(rowIndex - e1.up, e1.down - rowIndex);
  }
}
