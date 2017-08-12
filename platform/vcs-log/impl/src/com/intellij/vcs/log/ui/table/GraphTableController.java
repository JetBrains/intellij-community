/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.table;

import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.ui.HintHint;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.JBUI;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.EdgePrintElement;
import com.intellij.vcs.log.graph.NodePrintElement;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.paint.GraphCellPainter;
import com.intellij.vcs.log.paint.PositionUtil;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.frame.CommitPanel;
import com.intellij.vcs.log.ui.render.GraphCommitCellRenderer;
import com.intellij.vcs.log.ui.render.SimpleColoredComponentLinkMouseListener;
import com.intellij.vcs.log.util.VcsUserUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;

import static com.intellij.vcs.log.ui.table.GraphTableModel.*;

/**
 * Processes mouse clicks and moves on the table
 */
public class GraphTableController {
  @NotNull private final VcsLogGraphTable myTable;
  @NotNull private final AbstractVcsLogUi myUi;
  @NotNull private final VcsLogData myLogData;
  @NotNull private final GraphCellPainter myGraphCellPainter;
  @NotNull private final GraphCommitCellRenderer myCommitRenderer;

  public GraphTableController(@NotNull VcsLogGraphTable table,
                              @NotNull AbstractVcsLogUi ui,
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

    Selection previousSelection = myTable.getSelection();
    GraphAnswer<Integer> answer =
      myTable.getVisibleGraph().getActionController().performAction(new GraphAction.GraphActionImpl(printElement, actionType));
    handleGraphAnswer(answer, isClickOnGraphElement, previousSelection, e);
  }

  public void handleGraphAnswer(@Nullable GraphAnswer<Integer> answer,
                                boolean dataCouldChange,
                                @Nullable Selection previousSelection,
                                @Nullable MouseEvent e) {
    if (dataCouldChange) {
      myTable.getModel().fireTableDataChanged();

      // since fireTableDataChanged clears selection we restore it here
      if (previousSelection != null) {
        previousSelection
          .restore(myTable.getVisibleGraph(), answer == null || (answer.getCommitToJump() != null && answer.doJump()), false);
      }
    }

    myTable.repaint();

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
      if (e != null) showToolTip(getArrowTooltipText(answer.getCommitToJump(), row), e);
    }
  }

  @NotNull
  private Point calcPoint4Graph(@NotNull Point clickPoint) {
    int width = 0;
    for (int i = 0; i < myTable.getColumnModel().getColumnCount(); i++) {
      TableColumn column = myTable.getColumnModel().getColumn(i);
      if (column.getModelIndex() == COMMIT_COLUMN) break;
      width += column.getWidth();
    }
    return new Point(clickPoint.x - width, PositionUtil.getYInsideRow(clickPoint, myTable.getRowHeight()));
  }

  @NotNull
  private String getArrowTooltipText(int commit, @Nullable Integer row) {
    VcsShortCommitDetails details;
    if (row != null && row >= 0) {
      details = myTable.getModel().getShortDetails(row); // preload rows around the commit
    }
    else {
      details = myLogData.getMiniDetailsGetter().getCommitData(commit, Collections.singleton(commit)); // preload just the commit
    }

    String balloonText = "";
    if (details instanceof LoadingDetails) {
      CommitId commitId = myLogData.getCommitId(commit);
      if (commitId != null) {
        balloonText = "Jump to commit" + " " + commitId.getHash().toShortString();
        if (myUi.isMultipleRoots()) {
          balloonText += " in " + commitId.getRoot().getName();
        }
      }
    }
    else {
      balloonText = "Jump to <b>\"" +
                    StringUtil.shortenTextWithEllipsis(details.getSubject(), 50, 0, "...") +
                    "\"</b> by " +
                    VcsUserUtil.getShortPresentation(details.getAuthor()) +
                    CommitPanel.formatDateTime(details.getAuthorTime());
    }
    return balloonText;
  }

  private void showToolTip(@NotNull String text, @NotNull MouseEvent e) {
    // standard tooltip does not allow to customize its location, and locating tooltip above can obscure some important info
    Point point = new Point(e.getX() + 5, e.getY());

    JEditorPane tipComponent = IdeTooltipManager.initPane(text, new HintHint(myTable, point).setAwtTooltip(true), null);
    IdeTooltip tooltip = new IdeTooltip(myTable, point, new Wrapper(tipComponent)).setPreferredPosition(Balloon.Position.atRight);
    IdeTooltipManager.getInstance().show(tooltip, false);
  }

  private void showOrHideCommitTooltip(int row, int column, @NotNull MouseEvent e) {
    if (!showTooltip(row, column, e.getPoint(), false)) {
      if (IdeTooltipManager.getInstance().hasCurrent()) {
        IdeTooltipManager.getInstance().hideCurrent(e);
      }
    }
  }

  private boolean showTooltip(int row, int column, @NotNull Point point, boolean now) {
    JComponent tipComponent =
      myCommitRenderer.getTooltip(myTable.getValueAt(row, myTable.convertColumnIndexToView(column)), calcPoint4Graph(point), row);

    if (tipComponent != null) {
      myTable.getExpandableItemsHandler().setEnabled(false);
      IdeTooltip tooltip =
        new IdeTooltip(myTable, point, new Wrapper(tipComponent)).setPreferredPosition(Balloon.Position.below);
      IdeTooltipManager.getInstance().show(tooltip, now);
      return true;
    }
    return false;
  }

  public void showTooltip(int row) {
    Point point =
      new Point(getColumnLeftXCoordinate(myTable.convertColumnIndexToView(COMMIT_COLUMN)) + myCommitRenderer.getTooltipXCoordinate(row),
                row * myTable.getRowHeight() + myTable.getRowHeight() / 2);
    showTooltip(row, COMMIT_COLUMN, point, true);
  }

  private void performRootColumnAction() {
    VcsLogUiProperties properties = myUi.getProperties();
    if (myUi.isMultipleRoots() && properties.exists(MainVcsLogUiProperties.SHOW_ROOT_NAMES)) {
      VcsLogUtil.triggerUsage("RootColumnClick");
      properties.set(MainVcsLogUiProperties.SHOW_ROOT_NAMES, !properties.get(MainVcsLogUiProperties.SHOW_ROOT_NAMES));
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

  protected int getColumnLeftXCoordinate(int viewColumnIndex) {
    int x = 0;
    for (int i = 0; i < viewColumnIndex; i++) {
      x += myTable.getColumnModel().getColumn(i).getWidth();
    }
    return x;
  }

  private class MyMouseAdapter extends MouseAdapter {
    private static final int BORDER_THICKNESS = 3;
    @NotNull private final TableLinkMouseListener myLinkListener = new SimpleColoredComponentLinkMouseListener();

    @Override
    public void mouseClicked(MouseEvent e) {
      if (myLinkListener.onClick(e, e.getClickCount())) {
        return;
      }

      int c = myTable.columnAtPoint(e.getPoint());
      int column = myTable.convertColumnIndexToModel(c);
      if (e.getClickCount() == 2) {
        // when we reset column width, commit column eats all the remaining space
        // (or gives the required space)
        // so it is logical that we reset column width by right border if it is on the left of the commit column
        // and by the left border otherwise
        int commitColumnIndex = myTable.convertColumnIndexToView(COMMIT_COLUMN);
        boolean useLeftBorder = c > commitColumnIndex;
        if ((useLeftBorder ? isOnLeftBorder(e, c) : isOnRightBorder(e, c)) && (column == AUTHOR_COLUMN || column == DATE_COLUMN)) {
          myTable.resetColumnWidth(column);
        }
        else {
          // user may have clicked just outside of the border
          // in that case, c is not the column we are looking for
          int c2 =
            myTable.columnAtPoint(new Point(e.getPoint().x + (useLeftBorder ? 1 : -1) * JBUI.scale(BORDER_THICKNESS), e.getPoint().y));
          int column2 = myTable.convertColumnIndexToModel(c2);
          if ((useLeftBorder ? isOnLeftBorder(e, c2) : isOnRightBorder(e, c2)) && (column2 == AUTHOR_COLUMN || column2 == DATE_COLUMN)) {
            myTable.resetColumnWidth(column2);
          }
        }
      }

      int row = myTable.rowAtPoint(e.getPoint());
      if ((row >= 0 && row < myTable.getRowCount()) && e.getClickCount() == 1) {
        if (column == ROOT_COLUMN) {
          performRootColumnAction();
        }
        else if (column == COMMIT_COLUMN) {
          PrintElement printElement = findPrintElement(row, e);
          if (printElement != null) {
            performGraphAction(printElement, e, GraphAction.Type.MOUSE_CLICK);
          }
        }
      }
    }

    public boolean isOnLeftBorder(@NotNull MouseEvent e, int column) {
      return Math.abs(getColumnLeftXCoordinate(column) - e.getPoint().x) <= JBUI.scale(BORDER_THICKNESS);
    }

    public boolean isOnRightBorder(@NotNull MouseEvent e, int column) {
      return Math.abs(getColumnLeftXCoordinate(column) +
                      myTable.getColumnModel().getColumn(column).getWidth() - e.getPoint().x) <= JBUI.scale(BORDER_THICKNESS);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (myTable.isResizingColumns()) return;
      myTable.getExpandableItemsHandler().setEnabled(true);

      if (myLinkListener.getTagAt(e) != null) {
        myTable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return;
      }

      int row = myTable.rowAtPoint(e.getPoint());
      if (row >= 0 && row < myTable.getRowCount()) {
        int column = myTable.convertColumnIndexToModel(myTable.columnAtPoint(e.getPoint()));
        if (column == ROOT_COLUMN) {
          myTable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          return;
        }
        else if (column == COMMIT_COLUMN) {
          PrintElement printElement = findPrintElement(row, e);
          performGraphAction(printElement, e,
                             GraphAction.Type.MOUSE_OVER); // if printElement is null, still need to unselect whatever was selected in a graph
          if (printElement == null) {
            showOrHideCommitTooltip(row, column, e);
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
      myTable.getExpandableItemsHandler().setEnabled(true);
    }
  }
}
