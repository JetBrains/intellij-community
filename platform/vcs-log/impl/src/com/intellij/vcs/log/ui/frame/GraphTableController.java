/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.frame;

import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.EdgePrintElement;
import com.intellij.vcs.log.graph.NodePrintElement;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.paint.GraphCellPainter;
import com.intellij.vcs.log.paint.PositionUtil;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.render.GraphCommitCellRenderer;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;

/**
 * Processes mouse clicks and moves on the table
 */
public class GraphTableController {
  @NotNull private final VcsLogGraphTable myTable;
  @NotNull private final VcsLogUiImpl myUi;
  @NotNull private final VcsLogData myLogData;
  @NotNull private final GraphCellPainter myGraphCellPainter;
  @NotNull private final GraphCommitCellRenderer myCommitRenderer;

  public GraphTableController(@NotNull VcsLogGraphTable table,
                              @NotNull VcsLogUiImpl ui,
                              @NotNull VcsLogData logData,
                              @NotNull GraphCellPainter graphCellPainter,
                              @NotNull GraphCommitCellRenderer commitRenderer) {
    myTable = table;
    myUi = ui;
    myLogData = logData;
    myGraphCellPainter = graphCellPainter;
    myCommitRenderer = commitRenderer;

    MouseAdapter mouseAdapter = new MyMouseAdapter();
    table.addMouseMotionListener(mouseAdapter);
    table.addMouseListener(mouseAdapter);
  }

  @Nullable
  PrintElement findPrintElement(@NotNull MouseEvent e) {
    int row = myTable.rowAtPoint(e.getPoint());
    if (row >= 0 && row < myTable.getRowCount()) {
      return findPrintElement(row, e);
    }
    return null;
  }

  @Nullable
  private PrintElement findPrintElement(int row, @NotNull MouseEvent e) {
    Point point = calcPoint4Graph(e.getPoint());
    Collection<? extends PrintElement> printElements = myTable.getVisibleGraph().getRowInfo(row).getPrintElements();
    return myGraphCellPainter.getElementUnderCursor(printElements, point.x, point.y);
  }

  private void performGraphAction(@Nullable PrintElement printElement, @NotNull MouseEvent e, @NotNull GraphAction.Type actionType) {
    boolean isClickOnGraphElement = actionType == GraphAction.Type.MOUSE_CLICK && printElement != null;
    if (isClickOnGraphElement) {
      triggerElementClick(printElement);
    }

    VcsLogGraphTable.Selection previousSelection = myTable.getSelection();
    GraphAnswer<Integer> answer =
      myTable.getVisibleGraph().getActionController().performAction(new GraphAction.GraphActionImpl(printElement, actionType));
    handleGraphAnswer(answer, isClickOnGraphElement, previousSelection, e);
  }

  public void handleGraphAnswer(@Nullable GraphAnswer<Integer> answer,
                                boolean dataCouldChange,
                                @Nullable VcsLogGraphTable.Selection previousSelection,
                                @Nullable MouseEvent e) {
    if (dataCouldChange) {
      myTable.getModel().fireTableDataChanged();

      // since fireTableDataChanged clears selection we restore it here
      if (previousSelection != null) {
        previousSelection
          .restore(myTable.getVisibleGraph(), answer == null || (answer.getCommitToJump() != null && answer.doJump()), false);
      }
    }

    myUi.repaintUI(); // in case of repaintUI doing something more than just repainting this table in some distant future

    if (answer == null) {
      return;
    }

    if (answer.getCursorToSet() != null) {
      myTable.setCursor(answer.getCursorToSet());
    }
    if (answer.getCommitToJump() != null) {
      Integer row = myTable.getModel().getVisiblePack().getVisibleGraph().getVisibleRowIndex(answer.getCommitToJump());
      if (row != null && row >= 0 && answer.doJump()) {
        myTable.jumpToRow(row);
        // TODO wait for the full log and then jump
        return;
      }
      if (e != null) myTable.showToolTip(myTable.getArrowTooltipText(answer.getCommitToJump(), row), e);
    }
  }

  @NotNull
  private Point calcPoint4Graph(@NotNull Point clickPoint) {
    TableColumn rootColumn = myTable.getColumnModel().getColumn(GraphTableModel.ROOT_COLUMN);
    return new Point(clickPoint.x - (myLogData.isMultiRoot() ? rootColumn.getWidth() : 0),
                     PositionUtil.getYInsideRow(clickPoint, myTable.getRowHeight()));
  }

  private void showTooltip(int row, int column, @NotNull MouseEvent e) {
    JComponent tipComponent = myCommitRenderer.getTooltip(myTable.getValueAt(row, column), calcPoint4Graph(e.getPoint()),
                                                          myTable.getColumnModel().getColumn(GraphTableModel.COMMIT_COLUMN)
                                                            .getWidth());

    if (tipComponent != null) {
      IdeTooltip tooltip =
        new IdeTooltip(myTable, e.getPoint(), new Wrapper(tipComponent)).setPreferredPosition(Balloon.Position.below);
      IdeTooltipManager.getInstance().show(tooltip, false);
    }
  }

  private void performRootColumnAction() {
    if (myLogData.isMultiRoot()) {
      VcsLogUtil.triggerUsage("RootColumnClick");
      myUi.setShowRootNames(!myUi.isShowRootNames());
    }
  }

  private static void triggerElementClick(@NotNull PrintElement printElement) {
    if (printElement instanceof NodePrintElement) {
      VcsLogUtil.triggerUsage("GraphNodeClick");
    }
    else if (printElement instanceof EdgePrintElement) {
      if (((EdgePrintElement)printElement).hasArrow()) {
        VcsLogUtil.triggerUsage("GraphArrowClick");
      }
    }
  }

  private class MyMouseAdapter extends MouseAdapter {
    @NotNull private final TableLinkMouseListener myLinkListener = new TableLinkMouseListener();

    @Override
    public void mouseClicked(MouseEvent e) {
      if (myLinkListener.onClick(e, e.getClickCount())) {
        return;
      }

      int row = myTable.rowAtPoint(e.getPoint());
      if ((row >= 0 && row < myTable.getRowCount()) && e.getClickCount() == 1) {
        int column = myTable.convertColumnIndexToModel(myTable.columnAtPoint(e.getPoint()));
        if (column == GraphTableModel.ROOT_COLUMN) {
          performRootColumnAction();
        }
        else if (column == GraphTableModel.COMMIT_COLUMN) {
          PrintElement printElement = findPrintElement(row, e);
          if (printElement != null) {
            performGraphAction(printElement, e, GraphAction.Type.MOUSE_CLICK);
          }
        }
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (myTable.isResizingColumns()) return;

      if (myLinkListener.getTagAt(e) != null) {
        myTable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return;
      }

      int row = myTable.rowAtPoint(e.getPoint());
      if (row >= 0 && row < myTable.getRowCount()) {
        int column = myTable.convertColumnIndexToModel(myTable.columnAtPoint(e.getPoint()));
        if (column == GraphTableModel.ROOT_COLUMN) {
          myTable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          return;
        }
        else if (column == GraphTableModel.COMMIT_COLUMN) {
          PrintElement printElement = findPrintElement(row, e);
          performGraphAction(printElement, e,
                             GraphAction.Type.MOUSE_OVER); // if printElement is null, still need to unselect whatever was selected in a graph
          if (printElement == null) {
            showTooltip(row, column, e);
          }
          return;
        }
      }

      myTable.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      // Do nothing
    }

    @Override
    public void mouseExited(MouseEvent e) {
      // Do nothing
    }
  }
}
