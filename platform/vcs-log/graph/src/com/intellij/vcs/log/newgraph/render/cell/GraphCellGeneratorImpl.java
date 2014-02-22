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
import com.intellij.util.containers.DoubleArrayList;
import com.intellij.util.containers.SLRUMap;
import com.intellij.vcs.log.newgraph.gpaph.Edge;
import com.intellij.vcs.log.newgraph.gpaph.GraphElement;
import com.intellij.vcs.log.newgraph.gpaph.MutableGraph;
import com.intellij.vcs.log.newgraph.gpaph.Node;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GraphCellGeneratorImpl extends AbstractGraphCellGenerator {
  private static final int LONG_EDGE = 30;
  private static final int LONG_EDGE_ARROW = 1;

  private static final int VERY_LONG_EDGE = 1000;
  private static final int VERY_LONG_EDGE_ARROW = 250;
  private static final int CACHE_SIZE = 1000;


  protected final EdgesInRow myEdgesInRow;
  private final SLRUMap<Integer, List<GraphElement>> cache = new SLRUMap<Integer, List<GraphElement>>(CACHE_SIZE, CACHE_SIZE * 2);

  private boolean showLongEdges = false;

  protected GraphCellGeneratorImpl(@NotNull MutableGraph graph) {
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
          position = toPosition.get(myGraph.getNode(edge.getDownNodeVisibleIndex()));
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
        if (visibleRowIndex - edge.getUpNodeVisibleIndex() == getLongEdgeArrow())
          result.add(new SpecialRowElement(edge, position, SpecialRowElement.Type.DOWN_ARROW));

        if (edge.getDownNodeVisibleIndex() - visibleRowIndex == getLongEdgeArrow())
          result.add(new SpecialRowElement(edge, position, SpecialRowElement.Type.UP_ARROW));
      }
      position++;
    }
    return result;
  }

  public void setShowLongEdges(boolean showLongEdges) {
    this.showLongEdges = showLongEdges;
  }

  public void invalidate() {
    myEdgesInRow.invalidate();
    cache.clear();
  }

  private int getLongEdge() {
    if (showLongEdges) {
      return VERY_LONG_EDGE;
    } else {
      return LONG_EDGE;
    }
  }

  private int getLongEdgeArrow() {
    if (showLongEdges) {
      return VERY_LONG_EDGE_ARROW;
    } else {
      return LONG_EDGE_ARROW;
    }
  }

  private boolean edgeIsVisibleInRow(@NotNull Edge edge, int visibleRowIndex) {
    int edgeSize = edge.getDownNodeVisibleIndex() - edge.getUpNodeVisibleIndex();
    if (edgeSize < getLongEdge()) {
      return true;
    } else {
      return visibleRowIndex - edge.getUpNodeVisibleIndex() <= getLongEdgeArrow()
             || edge.getDownNodeVisibleIndex() - visibleRowIndex <= getLongEdgeArrow();
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
        return Double.compare(o1.getLayoutIndex(), o2.getLayoutIndex());
      }
    });

    cache.put(visibleRowIndex, result);
    return result;
  }
}
