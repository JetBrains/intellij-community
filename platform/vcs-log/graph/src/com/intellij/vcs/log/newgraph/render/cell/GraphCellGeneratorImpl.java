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

import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.SLRUMap;
import com.intellij.vcs.log.newgraph.SomeGraph;
import com.intellij.vcs.log.newgraph.gpaph.Edge;
import com.intellij.vcs.log.newgraph.gpaph.GraphElement;
import com.intellij.vcs.log.newgraph.gpaph.MutableGraph;
import com.intellij.vcs.log.newgraph.gpaph.Node;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GraphCellGeneratorImpl extends AbstractGraphCellGenerator {
  private static final int LONG_EDGE_SIZE = 30;
  private static final int LONG_EDGE_PART_SIZE = 1;

  private static final int VERY_LONG_EDGE_SIZE = 1000;
  private static final int VERY_LONG_EDGE_PART_SIZE = 250;
  private static final int CACHE_SIZE = 100;
  private static final boolean SHOW_ARROW_WHEN_SHOW_LONG_EDGES = true;


  protected final EdgesInRow myEdgesInRow;
  private final SLRUMap<Integer, List<GraphElement>> cache = new SLRUMap<Integer, List<GraphElement>>(CACHE_SIZE, CACHE_SIZE * 2);

  private boolean showLongEdges = false;

  public GraphCellGeneratorImpl(@NotNull MutableGraph graph) {
    super(graph);
    myEdgesInRow = new EdgesInRow(graph);
  }

  @Override
  protected int getCountVisibleElements(int visibleRowIndex) {
    return getSortedVisibleElementsInRow(visibleRowIndex).size();
  }

  @NotNull
  @Override
  protected List<ShortEdge> getDownShortEdges(int visibleRowIndex) {
    Function<Edge, Integer> endPosition = createEndPositionFunction(visibleRowIndex);

    List<ShortEdge> result = new ArrayList<ShortEdge>();
    List<GraphElement> visibleElements = getSortedVisibleElementsInRow(visibleRowIndex);

    for (int startPosition = 0; startPosition < visibleElements.size(); startPosition++) {
      GraphElement element = visibleElements.get(startPosition);
      if (element instanceof Node) {
        Node node = (Node) element;
        for (Edge edge : node.getDownEdges()) {
          int endPos = endPosition.fun(edge);
          if (endPos != -1)
            result.add(new ShortEdge(edge, startPosition, endPos));
        }
      }

      if (element instanceof Edge) {
        Edge edge = (Edge) element;
        int endPos = endPosition.fun(edge);
        if (endPos != -1)
          result.add(new ShortEdge(edge, startPosition, endPos));
      }
    }

    return result;
  }

  @NotNull
  private Function<Edge, Integer> createEndPositionFunction(int visibleRowIndex) {
    List<GraphElement> visibleElementsInNextRow = getSortedVisibleElementsInRow(visibleRowIndex + 1);

    final Map<GraphElement, Integer> toPosition = new HashMap<GraphElement, Integer>();
    for (int position = 0; position < visibleElementsInNextRow.size(); position++)
      toPosition.put(visibleElementsInNextRow.get(position), position);

    return new Function<Edge, Integer>() {
      @Override
      public Integer fun(Edge edge) {
        Integer position = toPosition.get(edge);
        if (position == null) {
          int downNodeVisibleIndex = edge.getDownNodeVisibleIndex();
          if (downNodeVisibleIndex != SomeGraph.NOT_LOAD_COMMIT)
            position = toPosition.get(myGraph.getNode(downNodeVisibleIndex));
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
  protected List<SpecialRowElement> getSpecialElements(int visibleRowIndex) {
    List<SpecialRowElement> result = new SmartList<SpecialRowElement>();
    int position = 0;
    for (GraphElement element : getSortedVisibleElementsInRow(visibleRowIndex)) {
      if (element instanceof Node) {
        result.add(new SpecialRowElement(element, position, SpecialRowElement.Type.NODE));
      }
      if (element instanceof Edge) {
        Edge edge = (Edge) element;
        int edgeSize = edge.getDownNodeVisibleIndex() - edge.getUpNodeVisibleIndex();
        int upOffset = visibleRowIndex - edge.getUpNodeVisibleIndex();
        int downOffset = edge.getDownNodeVisibleIndex() - visibleRowIndex;

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

  private static void addArrowIfNeeded(List<SpecialRowElement> result,
                                       int position,
                                       Edge edge,
                                       int upOffset,
                                       int downOffset,
                                       int edgePartSize) {
    if (upOffset == edgePartSize)
      result.add(new SpecialRowElement(edge, position, SpecialRowElement.Type.DOWN_ARROW));

    if (downOffset == edgePartSize)
      result.add(new SpecialRowElement(edge, position, SpecialRowElement.Type.UP_ARROW));
  }

  @Override
  public boolean isShowLongEdges() {
    return showLongEdges;
  }

  @Override
  public void setShowLongEdges(boolean showLongEdges) {
    this.showLongEdges = showLongEdges;
    invalidate();
  }

  @Override
  public void invalidate() {
    myEdgesInRow.invalidate();
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

  private boolean edgeIsVisibleInRow(@NotNull Edge edge, int visibleRowIndex) {
    int edgeSize = edge.getDownNodeVisibleIndex() - edge.getUpNodeVisibleIndex();
    if (edgeSize < getLongEdgeSize()) {
      return true;
    } else {
      return visibleRowIndex - edge.getUpNodeVisibleIndex() <= getEdgeShowPartSize()
             || edge.getDownNodeVisibleIndex() - visibleRowIndex <= getEdgeShowPartSize();
    }
  }

  @NotNull
  private List<GraphElement> getSortedVisibleElementsInRow(int visibleRowIndex) {
    List<GraphElement> graphElements = cache.get(visibleRowIndex);
    if (graphElements != null) {
      return graphElements;
    }

    List<GraphElement> result = new ArrayList<GraphElement>();
    result.add(myGraph.getNode(visibleRowIndex));

    for (Edge edge : myEdgesInRow.getEdgesInRow(visibleRowIndex)) {
      if (edgeIsVisibleInRow(edge, visibleRowIndex))
        result.add(edge);
    }

    Collections.sort(result, new Comparator<GraphElement>() {
      @Override
      public int compare(@NotNull GraphElement o1, @NotNull GraphElement o2) {
        int layoutIndex1 = o1.getLayoutIndex();
        int layoutIndex2 = o2.getLayoutIndex();
        if (layoutIndex1 != layoutIndex2)
          return layoutIndex1 - layoutIndex2;

        if (o1 instanceof Node)
          return 1;

        if (o2 instanceof Node)
          return -1;

        if (o1 instanceof Edge && o2 instanceof Edge) {
          Edge edge1 = (Edge)o1;
          Edge edge2 = (Edge)o2;
          if (edge1.getUpNodeVisibleIndex() != edge2.getUpNodeVisibleIndex())
            return edge1.getUpNodeVisibleIndex() - edge2.getUpNodeVisibleIndex();
          else
            return edge2.getDownNodeVisibleIndex() - edge1.getDownNodeVisibleIndex();
        }
        return 0;
      }
    });

    cache.put(visibleRowIndex, result);
    return result;
  }
}
