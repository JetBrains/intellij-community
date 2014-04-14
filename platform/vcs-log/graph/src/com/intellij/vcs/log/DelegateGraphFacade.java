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

package com.intellij.vcs.log;

import com.intellij.openapi.util.Condition;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.graph.*;
import com.intellij.vcs.log.graph.GraphAnswer;
import com.intellij.vcs.log.graph.actions.ActionController;
import com.intellij.vcs.log.graph.actions.GraphMouseAction;
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl;
import com.intellij.vcs.log.printer.idea.ColorGenerator;
import com.intellij.vcs.log.printer.idea.GraphCellPainter;
import com.intellij.vcs.log.printer.idea.PrintParameters;
import com.intellij.vcs.log.printer.idea.SimpleGraphCellPainter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DelegateGraphFacade implements GraphFacade {

  @NotNull
  private final PermanentGraphImpl<Integer> myPermanentGraph;
  private final GraphCellPainter myGraphCellPainter;

  @NotNull
  private VisibleGraph<Integer> myVisibleGraph;

  @Nullable
  private Set<Integer> myHeads = null;

  @Nullable
  private Condition<Integer> myVisibilityPredicate = null;

  public DelegateGraphFacade(@NotNull PermanentGraphImpl<Integer> permanentGraph, @NotNull ColorGenerator colorGenerator) {
    myPermanentGraph = permanentGraph;
    myGraphCellPainter = new SimpleGraphCellPainter(colorGenerator);
    updateVisibleGraph();
  }

  private void updateVisibleGraph() {
    myVisibleGraph = myPermanentGraph.setFilter(myHeads, myVisibilityPredicate);
  }

  @NotNull
  @Override
  public PaintInfo paint(int visibleRow) {
    Collection<PrintElement> printElements = myVisibleGraph.getRowInfo(visibleRow).getPrintElements();
    int maxIndex = 0;
    for (PrintElement printElement : printElements) {
      maxIndex = Math.max(maxIndex, printElement.getPositionInCurrentRow());
    }
    maxIndex++;
    final BufferedImage image =
      UIUtil.createImage(PrintParameters.WIDTH_NODE * (maxIndex + 4), PrintParameters.HEIGHT_CELL, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = image.createGraphics();
    myGraphCellPainter.draw(g2, printElements);

    final int width = maxIndex * PrintParameters.WIDTH_NODE;
    return new PaintInfo() {
      @NotNull
      @Override
      public Image getImage() {
        return image;
      }

      @Override
      public int getWidth() {
        return width;
      }
    };
  }

  @Nullable
  @Override
  public GraphAnswer performAction(@NotNull GraphAction action) {
    ActionController<Integer> actionController = myVisibleGraph.getActionController();
    if (action instanceof ClickGraphAction) {
      ClickGraphAction clickGraphAction = (ClickGraphAction)action;
      Point relativePoint = clickGraphAction.getRelativePoint();
      PrintElement printElement = null;
      if (relativePoint != null) {
        Collection<PrintElement> printElements = myVisibleGraph.getRowInfo(clickGraphAction.getRow()).getPrintElements();
        printElement = myGraphCellPainter.mouseOver(printElements, relativePoint.x, relativePoint.y);
      }
      return convert(actionController.performMouseAction(new GraphMouseActionImpl(printElement, GraphMouseAction.Type.CLICK)));
    }

    if (action instanceof MouseOverAction) {
      MouseOverAction mouseOverAction = (MouseOverAction)action;
      Point relativePoint = mouseOverAction.getRelativePoint();
      Collection<PrintElement> printElements = myVisibleGraph.getRowInfo(mouseOverAction.getRow()).getPrintElements();
      PrintElement printElement = myGraphCellPainter.mouseOver(printElements, relativePoint.x, relativePoint.y);

      return convert(actionController.performMouseAction(new GraphMouseActionImpl(printElement, GraphMouseAction.Type.OVER)));
    }

    if (action instanceof LongEdgesAction) {
      boolean shouldShowLongEdges = ((LongEdgesAction)action).shouldShowLongEdges();
      actionController.setLongEdgesHidden(!shouldShowLongEdges);
    }

    if (action instanceof LinearBranchesExpansionAction) {
      boolean shouldExpand = ((LinearBranchesExpansionAction)action).shouldExpand();
      actionController.setLinearBranchesExpansion(!shouldExpand);
    }

    return null;
  }

  @NotNull
  private GraphAnswer convert(@NotNull com.intellij.vcs.log.graph.actions.GraphAnswer<Integer> graphAnswer) {
    final Integer commitToJump = graphAnswer.getCommitToJump();
    final Cursor cursorToSet = graphAnswer.getCursorToSet();
    return new GraphAnswer() {
      @Nullable
      @Override
      public GraphChange getGraphChange() {
        if (commitToJump != null) {
          return new GraphChange() {};
        }
        return null;
      }

      @Nullable
      @Override
      public GraphActionRequest getActionRequest() {
        if (cursorToSet != null)
          return new ChangeCursorActionRequest(cursorToSet);
        if (commitToJump != null) {
          int visibleRowIndex = myVisibleGraph.getVisibleRowIndex(commitToJump);
          if (visibleRowIndex == -1)
            visibleRowIndex = Integer.MAX_VALUE;
          return new JumpToRowActionRequest(visibleRowIndex);
        }
        return null;
      }
    };
  }

  @NotNull
  @Override
  public List<GraphCommit<Integer>> getAllCommits() {
    return myPermanentGraph.getAllCommits();
  }

  @Override
  public int getCommitAtRow(int visibleRow) {
    return myVisibleGraph.getRowInfo(visibleRow).getCommit();
  }

  @Override
  public int getVisibleCommitCount() {
    return myVisibleGraph.getVisibleCommitCount();
  }

  @Override
  public void setVisibleBranches(@Nullable Collection<Integer> heads) {
    if (heads == null)
      myHeads = null;
    else
      myHeads = new HashSet<Integer>(heads);

    updateVisibleGraph();
  }

  @Override
  public void setFilter(@Nullable Condition<Integer> visibilityPredicate) {
    myVisibilityPredicate = visibilityPredicate;
    updateVisibleGraph();
  }

  @NotNull
  @Override
  public GraphInfoProvider getInfoProvider() {
    return new GraphInfoProvider() {
      @NotNull
      @Override
      public Set<Integer> getContainingBranches(int visibleRow) {
        return myPermanentGraph.getContainingBranches(getCommitAtRow(visibleRow));
      }

      @NotNull
      @Override
      public RowInfo getRowInfo(int visibleRow) {
        final Integer oneOfHeads = myVisibleGraph.getRowInfo(visibleRow).getOneOfHeads();
        return new RowInfo() {
          @Override
          public int getOneOfHeads() {
            return oneOfHeads;
          }
        };
      }

      @Override
      public boolean areLongEdgesHidden() {
        return myVisibleGraph.getActionController().areLongEdgesHidden();
      }
    };
  }

  private static class GraphMouseActionImpl implements GraphMouseAction {
    @Nullable
    private final PrintElement myAffectedElement;
    @NotNull
    private final Type myType;

    private GraphMouseActionImpl(@Nullable PrintElement affectedElement, @NotNull Type type) {
      myAffectedElement = affectedElement;
      myType = type;
    }

    @Nullable
    @Override
    public PrintElement getAffectedElement() {
      return myAffectedElement;
    }

    @NotNull
    @Override
    public Type getType() {
      return myType;
    }
  }
}
