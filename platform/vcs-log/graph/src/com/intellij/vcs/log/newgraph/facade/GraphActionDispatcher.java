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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.vcs.log.GraphCommit;
import com.intellij.vcs.log.graph.ChangeCursorActionRequest;
import com.intellij.vcs.log.graph.ClickGraphAction;
import com.intellij.vcs.log.graph.MouseOverAction;
import com.intellij.vcs.log.graph.*;
import com.intellij.vcs.log.newgraph.SomeGraph;
import com.intellij.vcs.log.newgraph.gpaph.Edge;
import com.intellij.vcs.log.newgraph.gpaph.GraphElement;
import com.intellij.vcs.log.newgraph.gpaph.actions.*;
import com.intellij.vcs.log.newgraph.render.cell.GraphCell;
import com.intellij.vcs.log.newgraph.render.cell.SpecialRowElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class GraphActionDispatcher {

  private static final Logger LOG = Logger.getInstance(GraphActionDispatcher.class);

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

    if (action instanceof LinearBranchesExpansionAction) {
      myGraphData.getMutableGraph().performAction(new LinearBranchesExpansionInternalGraphAction(((LinearBranchesExpansionAction)action).shouldExpand()));
      myGraphData.getGraphRender().invalidate();
      return ActionRequestGraphAnswer.JUMP_TO_FIRST_ROW;
    }

    if (action instanceof SelectAllRelativeCommitsAction) {
      selectAllRelativeCommits(((SelectAllRelativeCommitsAction)action));
    }

    return null;
  }

  private void selectAllRelativeCommits(@NotNull SelectAllRelativeCommitsAction action) {
    InternalGraphAction internalGraphAction;

    if (action == SelectAllRelativeCommitsAction.DESELECT_ALL) {
      internalGraphAction = SelectAllRelativeCommitsInternalGraphAction.DESELECT_ALL;
    } else {
      int visibleRowIndex = action.getVisibleRowIndex();
      myGraphData.assertRange(visibleRowIndex);
      internalGraphAction = new SelectAllRelativeCommitsInternalGraphAction(visibleRowIndex);
    }
    myGraphData.getMutableGraph().performAction(internalGraphAction);
  }

  @Nullable
  private GraphAnswer clickToGraph(@NotNull ClickGraphAction action) {
    int visibleRowIndex = action.getRow();
    if (visibleRowIndex >= myGraphData.getMutableGraph().getCountVisibleNodes())
      return null;

    Point clickPoint = action.getRelativePoint();
    if (clickPoint == null) {
      return null;
    }

    Pair<SpecialRowElement, GraphElement> arrowOrGraphElement = getOverArrowOrGraphElement(visibleRowIndex, clickPoint);
    if (arrowOrGraphElement.first != null) {
      int toRow;
      Edge edge = (Edge)arrowOrGraphElement.first.getElement();

      if (arrowOrGraphElement.first.getType() == SpecialRowElement.Type.DOWN_ARROW)
        toRow = edge.getDownNodeVisibleIndex();
      else
        toRow = edge.getUpNodeVisibleIndex();

      if (toRow == SomeGraph.NOT_LOAD_COMMIT) {
        int indexOfParentCommit = myGraphData.getMutableGraph().getNode(edge.getUpNodeVisibleIndex()).getDownEdges().indexOf(edge);

        int indexInPermanentGraph = myGraphData.getMutableGraph().getIndexInPermanentGraph(edge.getUpNodeVisibleIndex());
        int commitHashIndex = myGraphData.getPermanentGraph().getHashIndex(indexInPermanentGraph);

        GraphCommit commit = myGraphData.getCommitsWithNotLoadParentMap().get(commitHashIndex);
        if (indexOfParentCommit >= 0 && indexOfParentCommit < commit.getParentIndices().length) {
          return ActionRequestGraphAnswer.jumpToNotLoadCommit(commit.getParentIndices()[indexOfParentCommit]);
        } else {
          LOG.error("Jump to not load commit with bad edge index. " +
                    "Edge index: " + indexOfParentCommit + ", commit hash index: " + commitHashIndex +
                    "count parents commits: " + commit.getParentIndices().length + ".");
          return null;
        }
      }

      return ActionRequestGraphAnswer.jumpToRow(toRow);
    } else {
      int toRow = myGraphData.getMutableGraph().performAction(new ClickInternalGraphAction(arrowOrGraphElement.second));
      myGraphData.getMutableGraph().performAction(new MouseOverGraphElementInternalGraphAction(null));
      myGraphData.getGraphRender().invalidate();
      if (toRow != -1)
        return ActionRequestGraphAnswer.jumpToRow(toRow);
      return null; // TODO
    }
  }

  @Nullable
  private GraphAnswer mouseOverAction(@NotNull MouseOverAction action) {
    int visibleRowIndex = action.getRow();
    if (visibleRowIndex >= myGraphData.getMutableGraph().getCountVisibleNodes())
      return null;

    Pair<SpecialRowElement, GraphElement> arrowOrGraphElement = getOverArrowOrGraphElement(visibleRowIndex, action.getRelativePoint());
    if (arrowOrGraphElement.first != null) {
      myGraphData.getMutableGraph().performAction(new MouseOverGraphElementInternalGraphAction(null));
      myGraphData.getMutableGraph().performAction(new MouseOverArrowInternalGraphAction(arrowOrGraphElement.first));
      return ActionRequestGraphAnswer.CHANGE_CURSOR_TO_HAND_CURSOR;
    } else {
      myGraphData.getMutableGraph().performAction(new MouseOverArrowInternalGraphAction(null));
      myGraphData.getMutableGraph().performAction(new MouseOverGraphElementInternalGraphAction(arrowOrGraphElement.second));
      return ActionRequestGraphAnswer.CHANGE_CURSOR_TO_DEFAULT;
    }
  }


  @NotNull
  private Pair<SpecialRowElement, GraphElement> getOverArrowOrGraphElement(int visibleRowIndex, @NotNull Point point) {
    GraphCell graphCell = myGraphData.getGraphRender().getCellGenerator().getGraphCell(visibleRowIndex);

    SpecialRowElement overArrow = myGraphData.getGraphRender().getCellPainter().mouseOverArrow(graphCell, point.x, point.y);
    GraphElement overElement = myGraphData.getGraphRender().getCellPainter().mouseOver(graphCell, point.x, point.y);
    return new Pair<SpecialRowElement, GraphElement>(overArrow, overElement);
  }


  private static class ActionRequestGraphAnswer implements GraphAnswer {

    public final static GraphAnswer CHANGE_CURSOR_TO_HAND_CURSOR = changeCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    public final static GraphAnswer CHANGE_CURSOR_TO_DEFAULT = changeCursor(Cursor.getDefaultCursor());

    public final static GraphAnswer JUMP_TO_FIRST_ROW = jumpToRow(0);

    @NotNull
    public static GraphAnswer jumpToRow(int rowIndex) {
      return new ActionRequestGraphAnswer(new JumpToRowActionRequest(rowIndex), true);
    }

    @NotNull
    public static GraphAnswer jumpToNotLoadCommit(int commitHashIndex) {
      return new ActionRequestGraphAnswer(new JumpToNotLoadCommitActionRequest(commitHashIndex));
    }

    @NotNull
    public static GraphAnswer changeCursor(@NotNull Cursor cursor) {
      return new ActionRequestGraphAnswer(new ChangeCursorActionRequest(cursor));
    }

    private final static GraphChange SOME_CHANGE = new GraphChange() {};

    @NotNull
    private final GraphActionRequest myGraphActionRequest;

    private final boolean mySomebodyChanged;

    private ActionRequestGraphAnswer(@NotNull GraphActionRequest graphActionRequest) {
      this(graphActionRequest, false);
    }

    private ActionRequestGraphAnswer(@NotNull GraphActionRequest graphActionRequest, boolean somebodyChanged) {
      myGraphActionRequest = graphActionRequest;
      mySomebodyChanged = somebodyChanged;
    }

    @Nullable
    @Override
    public GraphChange getGraphChange() {
      if (mySomebodyChanged)
        return SOME_CHANGE;
      else
        return null;
    }

    @Nullable
    @Override
    public GraphActionRequest getActionRequest() {
      return myGraphActionRequest;
    }
  }

}
