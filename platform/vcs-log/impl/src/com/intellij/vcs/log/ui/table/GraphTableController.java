// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table;

import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.EdgePrintElement;
import com.intellij.vcs.log.graph.NodePrintElement;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.paint.GraphCellPainter;
import com.intellij.vcs.log.paint.PositionUtil;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogColorManager;
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil;
import com.intellij.vcs.log.ui.render.GraphCommitCellRenderer;
import com.intellij.vcs.log.ui.render.SimpleColoredComponentLinkMouseListener;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;

/**
 * Processes mouse clicks and moves on the table
 */
public class GraphTableController {
  @NotNull private final VcsLogGraphTable myTable;
  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogUiProperties myProperties;
  @NotNull private final VcsLogColorManager myColorManager;
  @NotNull private final GraphCellPainter myGraphCellPainter;
  @NotNull private final GraphCommitCellRenderer myCommitRenderer;

  public GraphTableController(@NotNull VcsLogData logData,
                              @NotNull VcsLogColorManager colorManager,
                              @NotNull VcsLogUiProperties properties,
                              @NotNull VcsLogGraphTable table,
                              @NotNull GraphCellPainter graphCellPainter,
                              @NotNull GraphCommitCellRenderer commitRenderer) {
    myTable = table;
    myLogData = logData;
    myProperties = properties;
    myColorManager = colorManager;

    myGraphCellPainter = graphCellPainter;
    myCommitRenderer = commitRenderer;

    MouseAdapter mouseAdapter = new MyMouseAdapter();
    table.addMouseMotionListener(mouseAdapter);
    table.addMouseListener(mouseAdapter);
  }

  boolean shouldSelectCell(@NotNull MouseEvent e) {
    int row = myTable.rowAtPoint(e.getPoint());
    if (row >= 0 && row < myTable.getRowCount()) {
      return findPrintElement(row, e) == null;
    }
    return true;
  }

  @Nullable
  private PrintElement findPrintElement(int row, @NotNull MouseEvent e) {
    Point point = getPointInCell(e.getPoint(), VcsLogColumn.COMMIT);
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

    Cursor cursorToSet = answer.getCursorToSet();
    if (cursorToSet != null) {
      myTable.setCursor(UIUtil.cursorIfNotDefault(cursorToSet));
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
  private Point getPointInCell(@NotNull Point clickPoint, @NotNull VcsLogColumn vcsLogColumn) {
    int width = 0;
    for (int i = 0; i < myTable.getColumnModel().getColumnCount(); i++) {
      TableColumn column = myTable.getColumnModel().getColumn(i);
      if (column.getModelIndex() == vcsLogColumn.ordinal()) break;
      width += column.getWidth();
    }
    return new Point(clickPoint.x - width, PositionUtil.getYInsideRow(clickPoint, myTable.getRowHeight()));
  }

  @NotNull
  private String getArrowTooltipText(int commit, @Nullable Integer row) {
    VcsShortCommitDetails details;
    if (row != null && row >= 0) {
      details = myTable.getModel().getCommitMetadata(row); // preload rows around the commit
    }
    else {
      details = myLogData.getMiniDetailsGetter().getCommitData(commit, Collections.singleton(commit)); // preload just the commit
    }

    String balloonText = "";
    if (details instanceof LoadingDetails) {
      CommitId commitId = myLogData.getCommitId(commit);
      if (commitId != null) {
        balloonText = "Jump to commit" + " " + commitId.getHash().toShortString();
        if (myColorManager.hasMultiplePaths()) {
          balloonText += " in " + commitId.getRoot().getName();
        }
      }
    }
    else {
      balloonText = "Jump to " + CommitPresentationUtil.getShortSummary(details);
    }
    return balloonText;
  }

  private void showToolTip(@NotNull String text, @NotNull MouseEvent e) {
    // standard tooltip does not allow to customize its location, and locating tooltip above can obscure some important info
    VcsLogUiUtil.showTooltip(myTable, new Point(e.getX() + 5, e.getY()), Balloon.Position.atRight, text);
  }

  private void showOrHideCommitTooltip(int row, @NotNull VcsLogColumn column, @NotNull MouseEvent e) {
    if (!showTooltip(row, column, e.getPoint(), false)) {
      if (IdeTooltipManager.getInstance().hasCurrent()) {
        IdeTooltipManager.getInstance().hideCurrent(e);
      }
    }
  }

  private boolean showTooltip(int row, @NotNull VcsLogColumn column, @NotNull Point point, boolean now) {
    JComponent tipComponent = myCommitRenderer.getTooltip(myTable.getValueAt(row, myTable.getColumnViewIndex(column)),
                                                          getPointInCell(point, column), row);

    if (tipComponent != null) {
      myTable.getExpandableItemsHandler().setEnabled(false);
      IdeTooltip tooltip = new IdeTooltip(myTable, point, new Wrapper(tipComponent)).setPreferredPosition(Balloon.Position.below);
      IdeTooltipManager.getInstance().show(tooltip, now);
      return true;
    }
    return false;
  }

  public void showTooltip(int row, @NotNull VcsLogColumn column) {
    if (column != VcsLogColumn.COMMIT) return;

    Point point = new Point(getColumnLeftXCoordinate(myTable.getColumnViewIndex(column)) + myCommitRenderer.getTooltipXCoordinate(row),
                            row * myTable.getRowHeight() + myTable.getRowHeight() / 2);
    showTooltip(row, column, point, true);
  }

  private void performRootColumnAction() {
    if (myColorManager.hasMultiplePaths() && myProperties.exists(CommonUiProperties.SHOW_ROOT_NAMES)) {
      triggerClick("root.column");
      myProperties.set(CommonUiProperties.SHOW_ROOT_NAMES, !myProperties.get(CommonUiProperties.SHOW_ROOT_NAMES));
    }
  }

  private static void triggerElementClick(@NotNull PrintElement printElement) {
    if (printElement instanceof NodePrintElement) {
      triggerClick("node");
    }
    else if (printElement instanceof EdgePrintElement) {
      if (((EdgePrintElement)printElement).hasArrow()) {
        triggerClick("arrow");
      }
    }
  }

  private static void triggerClick(@NotNull String target) {
    VcsLogUsageTriggerCollector.triggerUsage(VcsLogUsageTriggerCollector.VcsLogEvent.TABLE_CLICKED,
                                             data -> data.addData("target", target));
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
    @NotNull private final TableLinkMouseListener myLinkListener = new MyLinkMouseListener();
    @Nullable private Cursor myLastCursor = null;

    @Override
    public void mouseClicked(MouseEvent e) {
      if (myLinkListener.onClick(e, e.getClickCount())) {
        return;
      }

      int c = myTable.columnAtPoint(e.getPoint());
      VcsLogColumn column = myTable.getVcsLogColumn(c);
      if (column == null) return;
      if (e.getClickCount() == 2) {
        // when we reset column width, commit column eats all the remaining space
        // (or gives the required space)
        // so it is logical that we reset column width by right border if it is on the left of the commit column
        // and by the left border otherwise
        int commitColumnIndex = myTable.getColumnViewIndex(VcsLogColumn.COMMIT);
        boolean useLeftBorder = c > commitColumnIndex;
        if ((useLeftBorder ? isOnLeftBorder(e, c) : isOnRightBorder(e, c)) && column.isDynamic()) {
          myTable.resetColumnWidth(column);
        }
        else {
          // user may have clicked just outside of the border
          // in that case, c is not the column we are looking for
          int c2 =
            myTable.columnAtPoint(new Point(e.getPoint().x + (useLeftBorder ? 1 : -1) * JBUIScale.scale(BORDER_THICKNESS), e.getPoint().y));
          VcsLogColumn column2 = myTable.getVcsLogColumn(c2);
          if (column2 != null && (useLeftBorder ? isOnLeftBorder(e, c2) : isOnRightBorder(e, c2)) && column2.isDynamic()) {
            myTable.resetColumnWidth(column2);
          }
        }
      }

      int row = myTable.rowAtPoint(e.getPoint());
      if ((row >= 0 && row < myTable.getRowCount()) && e.getClickCount() == 1) {
        if (column == VcsLogColumn.ROOT) {
          performRootColumnAction();
        }
        else if (column == VcsLogColumn.COMMIT) {
          PrintElement printElement = findPrintElement(row, e);
          if (printElement != null) {
            performGraphAction(printElement, e, GraphAction.Type.MOUSE_CLICK);
          }
        }
      }
    }

    public boolean isOnLeftBorder(@NotNull MouseEvent e, int column) {
      return Math.abs(getColumnLeftXCoordinate(column) - e.getPoint().x) <= JBUIScale.scale(BORDER_THICKNESS);
    }

    public boolean isOnRightBorder(@NotNull MouseEvent e, int column) {
      return Math.abs(getColumnLeftXCoordinate(column) +
                      myTable.getColumnModel().getColumn(column).getWidth() - e.getPoint().x) <= JBUIScale.scale(BORDER_THICKNESS);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (myTable.getRowCount() == 0) return;
      if (myTable.isResizingColumns()) return;
      myTable.getExpandableItemsHandler().setEnabled(true);

      if (myLinkListener.getTagAt(e) != null) {
        swapCursor(Cursor.HAND_CURSOR);
        return;
      }

      int row = myTable.rowAtPoint(e.getPoint());
      if (row >= 0 && row < myTable.getRowCount()) {
        VcsLogColumn column = myTable.getVcsLogColumn(myTable.columnAtPoint(e.getPoint()));
        if (column == null) return;
        if (column == VcsLogColumn.ROOT) {
          swapCursor(Cursor.HAND_CURSOR);
          return;
        }
        else if (column == VcsLogColumn.COMMIT) {
          PrintElement printElement = findPrintElement(row, e);
          if (printElement == null) restoreCursor(Cursor.HAND_CURSOR);
          performGraphAction(printElement, e,
                             GraphAction.Type.MOUSE_OVER); // if printElement is null, still need to unselect whatever was selected in a graph
          if (printElement == null) {
            showOrHideCommitTooltip(row, column, e);
          }
          return;
        }
      }

      restoreCursor(Cursor.HAND_CURSOR);
    }

    private void swapCursor(int newCursorType) {
      if (myTable.getCursor().getType() != newCursorType && myLastCursor == null) {
        Cursor newCursor = Cursor.getPredefinedCursor(newCursorType);
        myLastCursor = myTable.getCursor();
        myTable.setCursor(newCursor);
      }
    }

    private void restoreCursor(int newCursorType) {
      if (myTable.getCursor().getType() == newCursorType) {
        myTable.setCursor(UIUtil.cursorIfNotDefault(myLastCursor));
        myLastCursor = null;
      }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      // Do nothing
    }

    @Override
    public void mouseExited(MouseEvent e) {
      myTable.getExpandableItemsHandler().setEnabled(true);
    }

    private class MyLinkMouseListener extends SimpleColoredComponentLinkMouseListener {
      @Nullable
      @Override
      public Object getTagAt(@NotNull MouseEvent e) {
        return ObjectUtils.tryCast(super.getTagAt(e), SimpleColoredComponent.BrowserLauncherTag.class);
      }
    }
  }
}
