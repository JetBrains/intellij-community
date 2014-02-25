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

package com.intellij.vcs.log.newgraph.facade;

import com.intellij.openapi.util.Pair;
import com.intellij.vcs.log.graph.*;
import com.intellij.vcs.log.graph.ClickGraphAction;
import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.newgraph.gpaph.Edge;
import com.intellij.vcs.log.newgraph.gpaph.GraphElement;
import com.intellij.vcs.log.newgraph.gpaph.MutableGraph;
import com.intellij.vcs.log.newgraph.gpaph.actions.*;
import com.intellij.vcs.log.newgraph.render.GraphRender;
import com.intellij.vcs.log.newgraph.render.cell.GraphCell;
import com.intellij.vcs.log.newgraph.render.cell.SpecialRowElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class GraphActionDispatcher {
  private final PermanentGraph myPermanentGraph;
  private final MutableGraph myMutableGraph;
  private final GraphRender myGraphRender;

  public GraphActionDispatcher(PermanentGraph permanentGraph, MutableGraph mutableGraph, GraphRender graphRender) {
    myPermanentGraph = permanentGraph;
    myMutableGraph = mutableGraph;
    myGraphRender = graphRender;
  }

  @Nullable
  public GraphAnswer performAction(@NotNull GraphAction action) {
    if (action instanceof LongEdgesAction) {
      myGraphRender.setShowLongEdges(((LongEdgesAction)action).shouldShowLongEdges());
    }

    if (action instanceof MouseOverAction) {
      return mouseOverAction((MouseOverAction)action);
    }

    if (action instanceof ClickGraphAction) {
      return clickToGraph((ClickGraphAction)action);
    }

    return null;
  }

  @Nullable
  private GraphAnswer clickToRow(int visibleRowIndex) {
    myMutableGraph.performAction(new RowClickInternalGraphAction(visibleRowIndex));
    return null;
  }

  @Nullable
  private GraphAnswer clickToGraph(@NotNull ClickGraphAction action) {
    int visibleRowIndex = action.getRow();
    Point clickPoint = action.getRelativePoint();
    if (clickPoint == null)
      return clickToRow(visibleRowIndex);

    Pair<SpecialRowElement, GraphElement> arrowOrGraphElement = getOverArrowOrGraphElement(visibleRowIndex, clickPoint);
    if (arrowOrGraphElement.first != null) {
      int toRow;
      Edge edge = (Edge)arrowOrGraphElement.first.getElement();

      if (arrowOrGraphElement.first.getType() == SpecialRowElement.Type.DOWN_ARROW)
        toRow = edge.getDownNodeVisibleIndex();
      else
        toRow = edge.getUpNodeVisibleIndex();
      return new JumpToRowAnswer(toRow);
    } else {
      myMutableGraph.performAction(new ClickInternalGraphAction(arrowOrGraphElement.second));
      myGraphRender.invalidate();
      return null; // TODO
    }
  }

  @Nullable
  private GraphAnswer mouseOverAction(@NotNull MouseOverAction action) {
    Pair<SpecialRowElement, GraphElement> arrowOrGraphElement = getOverArrowOrGraphElement(action.getRow(), action.getRelativePoint());
    if (arrowOrGraphElement.first != null) {
      myMutableGraph.performAction(new MouseOverGraphElementInternalGraphAction(null));
      myMutableGraph.performAction(new MouseOverArrowInternalGraphAction(arrowOrGraphElement.first));
      return ChangeCursorAnswer.HAND_CURSOR;
    } else {
      myMutableGraph.performAction(new MouseOverArrowInternalGraphAction(null));
      myMutableGraph.performAction(new MouseOverGraphElementInternalGraphAction(arrowOrGraphElement.second));
      return ChangeCursorAnswer.DEFAULT_CURSOR;
    }
  }


  @NotNull
  private Pair<SpecialRowElement, GraphElement> getOverArrowOrGraphElement(int visibleRowIndex, @NotNull Point point) {
    GraphCell graphCell = myGraphRender.getCellGenerator().getGraphCell(visibleRowIndex);

    SpecialRowElement overArrow = myGraphRender.getCellPainter().mouseOverArrow(graphCell, point.x, point.y);
    GraphElement overElement = myGraphRender.getCellPainter().mouseOver(graphCell, point.x, point.y);
    return new Pair<SpecialRowElement, GraphElement>(overArrow, overElement);
  }


  private static class JumpToRowAnswer implements GraphAnswer {
    private final int myRow;

    public JumpToRowAnswer(int row) {
      myRow = row;
    }

    @Nullable
    @Override
    public GraphChange getGraphChange() {
      return null;
    }

    @Nullable
    @Override
    public GraphActionRequest getActionRequest() {
      return new JumpToRowActionRequest(myRow);
    }

  }

  private static class ChangeCursorAnswer implements GraphAnswer {
    public final static ChangeCursorAnswer HAND_CURSOR = new ChangeCursorAnswer(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    public final static ChangeCursorAnswer DEFAULT_CURSOR = new ChangeCursorAnswer(Cursor.getDefaultCursor());

    private final Cursor myCursor;

    private ChangeCursorAnswer(@NotNull Cursor cursor) {
      myCursor = cursor;
    }

    @Nullable
    @Override
    public GraphChange getGraphChange() {
      return null;
    }

    @Nullable
    @Override
    public GraphActionRequest getActionRequest() {
      return new ChangeCursorActionRequest(myCursor);
    }
  }
}
