// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table;

import com.intellij.openapi.vcs.changes.issueLinks.TableLinkMouseListener;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.render.SimpleColoredComponentLinkMouseListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;

/**
 * Processes mouse clicks and moves on the table
 */
public class GraphTableController {
  @NotNull private final VcsLogGraphTable myTable;
  @NotNull private final MyMouseAdapter myMouseAdapter;

  public GraphTableController(@NotNull VcsLogGraphTable table) {
    myTable = table;

    myMouseAdapter = new MyMouseAdapter();
    table.addMouseMotionListener(myMouseAdapter);
    table.addMouseListener(myMouseAdapter);
  }

  @Nullable
  private VcsLogCellController getController(@NotNull VcsLogColumn column) {
    TableColumn tableColumn = myTable.getTableColumn(column);
    if (tableColumn == null) return null;

    TableCellRenderer renderer = myTable.getDefaultRenderer(column.getContentClass());
    if (!(renderer instanceof VcsLogCellRenderer)) return null;
    return ((VcsLogCellRenderer)renderer).getCellController();
  }

  boolean shouldSelectCell(@NotNull MouseEvent e) {
    int row = myTable.rowAtPoint(e.getPoint());
    if (row < 0 || row >= myTable.getRowCount()) return true;

    VcsLogColumn column = myTable.getVcsLogColumn(myTable.columnAtPoint(e.getPoint()));
    if (column == null) return true;
    VcsLogCellController controller = getController(column);
    if (controller == null) return true;

    return controller.shouldSelectCell(row, e);
  }

  public void handleGraphAnswer(@Nullable GraphAnswer<Integer> answer) {
    GraphCommitCellController controller = (GraphCommitCellController)Objects.requireNonNull(getController(VcsLogColumn.COMMIT));
    Cursor cursor = controller.handleGraphAnswer(answer, true, null, null);
    myMouseAdapter.handleCursor(cursor);
  }

  public void showTooltip(int row, @NotNull VcsLogColumn column) {
    if (column != VcsLogColumn.COMMIT) return;

    GraphCommitCellController controller = (GraphCommitCellController)Objects.requireNonNull(getController(column));
    controller.showTooltip(row);
  }

  public static void triggerClick(@NotNull String target) {
    VcsLogUsageTriggerCollector.triggerUsage(VcsLogUsageTriggerCollector.VcsLogEvent.TABLE_CLICKED,
                                             data -> data.addData("target", target));
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
        VcsLogCellController controller = getController(column);
        if (controller != null) {
          Cursor cursor = controller.performMouseClick(row, e);
          handleCursor(cursor);
        }
      }
    }

    public boolean isOnLeftBorder(@NotNull MouseEvent e, int column) {
      return Math.abs(myTable.getColumnLeftXCoordinate(column) - e.getPoint().x) <= JBUIScale.scale(BORDER_THICKNESS);
    }

    public boolean isOnRightBorder(@NotNull MouseEvent e, int column) {
      return Math.abs(myTable.getColumnLeftXCoordinate(column) +
                      myTable.getColumnModel().getColumn(column).getWidth() - e.getPoint().x) <= JBUIScale.scale(BORDER_THICKNESS);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (myTable.getRowCount() == 0) return;
      if (myTable.isResizingColumns()) return;
      myTable.getExpandableItemsHandler().setEnabled(true);

      if (myLinkListener.getTagAt(e) != null) {
        swapCursor();
        return;
      }

      int row = myTable.rowAtPoint(e.getPoint());
      if (row >= 0 && row < myTable.getRowCount()) {
        VcsLogColumn column = myTable.getVcsLogColumn(myTable.columnAtPoint(e.getPoint()));
        if (column == null) return;

        VcsLogCellController controller = getController(column);
        if (controller != null) {
          Cursor cursor = controller.performMouseMove(row, e);
          handleCursor(cursor);
          return;
        }
      }

      restoreCursor();
    }

    private void handleCursor(@Nullable Cursor cursor) {
      if (cursor != null) {
        if (cursor.getType() == Cursor.DEFAULT_CURSOR) {
          restoreCursor();
        }
        else if (cursor.getType() == Cursor.HAND_CURSOR) {
          swapCursor();
        }
      }
    }

    private void swapCursor() {
      if (myTable.getCursor().getType() != Cursor.HAND_CURSOR && myLastCursor == null) {
        Cursor newCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        myLastCursor = myTable.getCursor();
        myTable.setCursor(newCursor);
      }
    }

    private void restoreCursor() {
      if (myTable.getCursor().getType() == Cursor.HAND_CURSOR) {
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
