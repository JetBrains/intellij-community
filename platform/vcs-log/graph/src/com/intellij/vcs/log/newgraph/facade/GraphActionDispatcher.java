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
import com.intellij.vcs.log.newgraph.gpaph.Edge;
import com.intellij.vcs.log.newgraph.gpaph.GraphElement;
import com.intellij.vcs.log.newgraph.gpaph.actions.ClickInternalGraphAction;
import com.intellij.vcs.log.newgraph.gpaph.actions.MouseOverArrowInternalGraphAction;
import com.intellij.vcs.log.newgraph.gpaph.actions.MouseOverGraphElementInternalGraphAction;
import com.intellij.vcs.log.newgraph.gpaph.actions.RowClickInternalGraphAction;
import com.intellij.vcs.log.newgraph.render.cell.GraphCell;
import com.intellij.vcs.log.newgraph.render.cell.SpecialRowElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class GraphActionDispatcher {
  
  @NotNull
  private final GraphData myGraphData;
  
  public GraphActionDispatcher(@NotNull GraphData graphData) {
    myGraphData = graphData;
  }

  @Nullable
  public GraphAnswer performAction(@NotNull GraphAction action) {
    if (action instanceof LongEdgesAction) {
      myGraphData.getGraphRender().setShowLongEdges(((LongEdgesAction)action).shouldShowLongEdges());
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
    myGraphData.getMutableGraph().performAction(new RowClickInternalGraphAction(visibleRowIndex));
    return null;
  }

  @Nullable
  private GraphAnswer clickToGraph(@NotNull ClickGraphAction action) {
    int visibleRowIndex = action.getRow();
    if (visibleRowIndex >= myGraphData.getMutableGraph().getCountVisibleNodes())
      return null;

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
      int toRow = myGraphData.getMutableGraph().performAction(new ClickInternalGraphAction(arrowOrGraphElement.second));
      myGraphData.getMutableGraph().performAction(new MouseOverGraphElementInternalGraphAction(null));
      myGraphData.getGraphRender().invalidate();
      if (toRow != -1)
        return new JumpToRowAnswer(toRow);
      return null; // TODO
    }
  }

  @Nullable
  private GraphAnswer mouseOverAction(@NotNull MouseOverAction action) {
    if (action.getRow() >= myGraphData.getMutableGraph().getCountVisibleNodes())
      return null;

    Pair<SpecialRowElement, GraphElement> arrowOrGraphElement = getOverArrowOrGraphElement(action.getRow(), action.getRelativePoint());
    if (arrowOrGraphElement.first != null) {
      myGraphData.getMutableGraph().performAction(new MouseOverGraphElementInternalGraphAction(null));
      myGraphData.getMutableGraph().performAction(new MouseOverArrowInternalGraphAction(arrowOrGraphElement.first));
      return ChangeCursorAnswer.HAND_CURSOR;
    } else {
      myGraphData.getMutableGraph().performAction(new MouseOverArrowInternalGraphAction(null));
      myGraphData.getMutableGraph().performAction(new MouseOverGraphElementInternalGraphAction(arrowOrGraphElement.second));
      return ChangeCursorAnswer.DEFAULT_CURSOR;
    }
  }


  @NotNull
  private Pair<SpecialRowElement, GraphElement> getOverArrowOrGraphElement(int visibleRowIndex, @NotNull Point point) {
    GraphCell graphCell = myGraphData.getGraphRender().getCellGenerator().getGraphCell(visibleRowIndex);

    SpecialRowElement overArrow = myGraphData.getGraphRender().getCellPainter().mouseOverArrow(graphCell, point.x, point.y);
    GraphElement overElement = myGraphData.getGraphRender().getCellPainter().mouseOver(graphCell, point.x, point.y);
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
