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

import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.SLRUMap;
import com.intellij.vcs.log.graph.SimplePrintElement;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.PrintedLinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.printer.PrintElementsManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PrintElementGeneratorImpl extends AbstractPrintElementGenerator {
  private static final int LONG_EDGE_SIZE = 30;
  private static final int LONG_EDGE_PART_SIZE = 1;

  private static final int VERY_LONG_EDGE_SIZE = 1000;
  private static final int VERY_LONG_EDGE_PART_SIZE = 250;
  private static final int CACHE_SIZE = 100;
  private static final boolean SHOW_ARROW_WHEN_SHOW_LONG_EDGES = true;


  @NotNull
  private final SLRUMap<Integer, List<GraphElement>> cache = new SLRUMap<Integer, List<GraphElement>>(CACHE_SIZE, CACHE_SIZE * 2);
  @NotNull
  private final EdgesInRowGenerator myEdgesInRowGenerator;
  @NotNull
  private final GraphElementComparator myGraphElementComparator;

  private boolean showLongEdges = false;

  public PrintElementGeneratorImpl(@NotNull PrintedLinearGraph graph, @NotNull PrintElementsManager printElementsManager) {
    super(graph, printElementsManager);
    myEdgesInRowGenerator = new EdgesInRowGenerator(graph);
    myGraphElementComparator = new GraphElementComparator();
  }

  @NotNull
  @Override
  protected List<ShortEdge> getDownShortEdges(int rowIndex) {
    Function<GraphEdge, Integer> endPosition = createEndPositionFunction(rowIndex);

    List<ShortEdge> result = new ArrayList<ShortEdge>();
    List<GraphElement> visibleElements = getSortedVisibleElementsInRow(rowIndex);

    for (int startPosition = 0; startPosition < visibleElements.size(); startPosition++) {
      GraphElement element = visibleElements.get(startPosition);
      if (element instanceof GraphNode) {
        for (GraphEdge edge : myEdgesInRowGenerator.createDownEdges(((GraphNode)element).getNodeIndex())) {
          int endPos = endPosition.fun(edge);
          if (endPos != -1)
            result.add(new ShortEdge(edge, startPosition, endPos));
        }
      }

      if (element instanceof GraphEdge) {
        GraphEdge edge = (GraphEdge) element;
        int endPos = endPosition.fun(edge);
        if (endPos != -1)
          result.add(new ShortEdge(edge, startPosition, endPos));
      }
    }

    return result;
  }

  @NotNull
  private Function<GraphEdge, Integer> createEndPositionFunction(int visibleRowIndex) {
    List<GraphElement> visibleElementsInNextRow = getSortedVisibleElementsInRow(visibleRowIndex + 1);

    final Map<GraphElement, Integer> toPosition = new HashMap<GraphElement, Integer>();
    for (int position = 0; position < visibleElementsInNextRow.size(); position++)
      toPosition.put(visibleElementsInNextRow.get(position), position);

    return new Function<GraphEdge, Integer>() {
      @Override
      public Integer fun(GraphEdge edge) {
        Integer position = toPosition.get(edge);
        if (position == null) {
          int downNodeVisibleIndex = edge.getDownNodeIndex();
          if (downNodeVisibleIndex != LinearGraph.NOT_LOAD_COMMIT)
            position = toPosition.get(new GraphNode(downNodeVisibleIndex));
        }

        if (position == null) {
          // i.e. is long edge with arrow
          return -1;
        } else {
          return position;
        }
      }
    };
  }

  @NotNull
  @Override
  protected List<SimpleRowElement> getSimpleRowElements(int visibleRowIndex) {
    List<SimpleRowElement> result = new SmartList<SimpleRowElement>();
    int position = 0;
    for (GraphElement element : getSortedVisibleElementsInRow(visibleRowIndex)) {
      if (element instanceof GraphNode) {
        result.add(new SimpleRowElement(element, SimplePrintElement.Type.NODE, position));
      }
      if (element instanceof GraphEdge) {
        GraphEdge edge = (GraphEdge)element;
        int edgeSize = edge.getDownNodeIndex() - edge.getUpNodeIndex();
        int upOffset = visibleRowIndex - edge.getUpNodeIndex();
        int downOffset = edge.getDownNodeIndex() - visibleRowIndex;

        if (edgeSize >= LONG_EDGE_SIZE) {
          if (!showLongEdges) {
            addArrowIfNeeded(result, position, edge, upOffset, downOffset, LONG_EDGE_PART_SIZE);
          } else {
            if (SHOW_ARROW_WHEN_SHOW_LONG_EDGES)
              addArrowIfNeeded(result, position, edge, upOffset, downOffset, LONG_EDGE_PART_SIZE);

            if (edgeSize >= VERY_LONG_EDGE_SIZE)
              addArrowIfNeeded(result, position, edge, upOffset, downOffset, VERY_LONG_EDGE_PART_SIZE);
          }
        }

      }
      position++;
    }
    return result;
  }

  private static void addArrowIfNeeded(List<SimpleRowElement> result,
                                       int position,
                                       GraphEdge edge,
                                       int upOffset,
                                       int downOffset,
                                       int edgePartSize) {
    if (upOffset == edgePartSize)
      result.add(new SimpleRowElement(edge, SimplePrintElement.Type.DOWN_ARROW, position));

    if (downOffset == edgePartSize)
      result.add(new SimpleRowElement(edge, SimplePrintElement.Type.UP_ARROW, position));
  }

  @Override
  public boolean areLongEdgesHidden() {
    return !showLongEdges;
  }

  @Override
  public void setLongEdgesHidden(boolean longEdgesHidden) {
    showLongEdges = !longEdgesHidden;
    invalidate();
  }

  @Override
  public void invalidate() {
    myEdgesInRowGenerator.invalidate();
    cache.clear();
  }

  private int getLongEdgeSize() {
    if (showLongEdges)
      return VERY_LONG_EDGE_SIZE;
    else
      return LONG_EDGE_SIZE;
  }

  private int getEdgeShowPartSize() {
    if (showLongEdges)
      return VERY_LONG_EDGE_PART_SIZE;
    else
      return LONG_EDGE_PART_SIZE;
  }

  private boolean edgeIsVisibleInRow(@NotNull GraphEdge edge, int visibleRowIndex) {
    int edgeSize = edge.getDownNodeIndex() - edge.getUpNodeIndex();
    if (edgeSize < getLongEdgeSize()) {
      return true;
    } else {
      return visibleRowIndex - edge.getUpNodeIndex() <= getEdgeShowPartSize()
             || edge.getDownNodeIndex() - visibleRowIndex <= getEdgeShowPartSize();
    }
  }

  @NotNull
  private List<GraphElement> getSortedVisibleElementsInRow(int rowIndex) {
    List<GraphElement> graphElements = cache.get(rowIndex);
    if (graphElements != null) {
      return graphElements;
    }

    List<GraphElement> result = new ArrayList<GraphElement>();
    result.add(new GraphNode(rowIndex));

    for (GraphEdge edge : myEdgesInRowGenerator.getEdgesInRow(rowIndex)) {
      if (edgeIsVisibleInRow(edge, rowIndex))
        result.add(edge);
    }

    Collections.sort(result, myGraphElementComparator);
    cache.put(rowIndex, result);
    return result;
  }

  private class GraphElementComparator implements Comparator<GraphElement> {
    @Override
    public int compare(@NotNull GraphElement o1, @NotNull GraphElement o2) {
      if (o1 instanceof GraphEdge && o2 instanceof GraphEdge) {
        int upNodeIndex1 = ((GraphEdge)o1).getUpNodeIndex();
        int upNodeIndex2 = ((GraphEdge)o2).getUpNodeIndex();

        if (upNodeIndex1 == upNodeIndex2) {
          int downNodeIndex1 = ((GraphEdge)o1).getDownNodeIndex();
          int downNodeIndex2 = ((GraphEdge)o2).getDownNodeIndex();
          if (downNodeIndex1 == LinearGraph.NOT_LOAD_COMMIT)
            return 1;
          if (downNodeIndex2 == LinearGraph.NOT_LOAD_COMMIT)
            return -1;

          if (getLayoutIndex(downNodeIndex1) != getLayoutIndex(downNodeIndex2))
            return getLayoutIndex(downNodeIndex1) - getLayoutIndex(downNodeIndex2);
          else
            return downNodeIndex1 - downNodeIndex2;
        }

        if (upNodeIndex1 < upNodeIndex2)
          return compare(o1, new GraphNode(upNodeIndex2));
        else
          return compare(new GraphNode(upNodeIndex1), o2);
      }

      if (o1 instanceof GraphEdge && o2 instanceof GraphNode)
        return compare2((GraphEdge) o1, (GraphNode) o2);

      if (o1 instanceof GraphNode && o2 instanceof GraphEdge)
        return - compare2((GraphEdge) o2, (GraphNode) o1);

      assert false;
      return 0;
    }

    private int compare2(@NotNull GraphEdge edge, @NotNull GraphNode node) {
      int upEdgeLI = getLayoutIndex(edge.getUpNodeIndex());
      int downEdgeLI = upEdgeLI;
      if (edge.getDownNodeIndex() != LinearGraph.NOT_LOAD_COMMIT)
        downEdgeLI = getLayoutIndex(edge.getDownNodeIndex());

      int nodeLI = getLayoutIndex(node.getNodeIndex());
      if (Math.max(upEdgeLI, downEdgeLI) != nodeLI)
        return Math.max(upEdgeLI, downEdgeLI) - nodeLI;
      else
        return edge.getUpNodeIndex() - node.getNodeIndex();
    }

    private int getLayoutIndex(int nodeIndex) {
      return myPrintedLinearGraph.getLayoutIndex(nodeIndex);
    }
  }
}
