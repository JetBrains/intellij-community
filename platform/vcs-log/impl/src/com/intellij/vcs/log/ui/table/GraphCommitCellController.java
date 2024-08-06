// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table;

import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.EdgePrintElement;
import com.intellij.vcs.log.graph.NodePrintElement;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.actions.GraphAnswer;
import com.intellij.vcs.log.paint.GraphCellPainter;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil;
import com.intellij.vcs.log.ui.table.column.Commit;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;

@ApiStatus.Internal
public abstract class GraphCommitCellController implements VcsLogCellController {
  private final @NotNull VcsLogData myLogData;
  private final @NotNull VcsLogGraphTable myTable;
  private final @NotNull GraphCellPainter myGraphCellPainter;

  public GraphCommitCellController(@NotNull VcsLogData logData,
                                   @NotNull VcsLogGraphTable table,
                                   @NotNull GraphCellPainter painter) {
    myLogData = logData;
    myTable = table;
    myGraphCellPainter = painter;
  }

  protected abstract int getTooltipXCoordinate(int row);

  protected abstract @Nullable JComponent getTooltip(@NotNull Object value, @NotNull Point point, int row);

  @Override
  public @Nullable Cursor performMouseClick(int row, @NotNull MouseEvent e) {
    PrintElement printElement = findPrintElement(row, myTable.getPointInCell(e.getPoint(), Commit.INSTANCE));
    if (printElement != null) {
      return performGraphAction(printElement, e, GraphAction.Type.MOUSE_CLICK);
    }
    return null;
  }

  @Override
  public @NotNull MouseMoveResult performMouseMove(int row, @NotNull MouseEvent e) {
    Point pointInCell = myTable.getPointInCell(e.getPoint(), Commit.INSTANCE);
    PrintElement printElement = findPrintElement(row, pointInCell);
    Cursor cursor = performGraphAction(printElement, e, GraphAction.Type.MOUSE_OVER);
    // if printElement is null, still need to unselect whatever was selected in a graph
    if (printElement == null) {
      if (myTable.getExpandableItemsHandler().getExpandedItems().isEmpty() && showTooltip(row, pointInCell, e.getPoint(), false)) {
        return new MouseMoveResult(cursor, false);
      }
      else if (IdeTooltipManager.getInstance().hasCurrent()) {
        IdeTooltipManager.getInstance().hideCurrent(e);
      }
    }
    return new MouseMoveResult(cursor, cursor != null && cursor.getType() == Cursor.DEFAULT_CURSOR);
  }

  @Override
  public boolean shouldSelectCell(int row, @NotNull MouseEvent e) {
    return findPrintElement(row, myTable.getPointInCell(e.getPoint(), Commit.INSTANCE)) == null;
  }

  private @Nullable PrintElement findPrintElement(int row, @NotNull Point pointInCell) {
    Collection<? extends PrintElement> printElements = myTable.getModel().getRowInfo(row).getPrintElements();
    return myGraphCellPainter.getElementUnderCursor(ScaleContext.create(myTable), printElements, pointInCell.x, pointInCell.y);
  }

  private @Nullable Cursor performGraphAction(@Nullable PrintElement printElement,
                                              @NotNull MouseEvent e,
                                              @NotNull GraphAction.Type actionType) {
    boolean isClickOnGraphElement = actionType == GraphAction.Type.MOUSE_CLICK && printElement != null;
    if (isClickOnGraphElement) {
      triggerElementClick(printElement);
    }

    SelectionSnapshot previousSelection = myTable.getSelectionSnapshot();
    GraphAnswer<Integer> answer =
      myTable.getVisibleGraph().getActionController().performAction(new GraphAction.GraphActionImpl(printElement, actionType));
    return handleGraphAnswer(answer, isClickOnGraphElement, previousSelection, e);
  }

  @Nullable
  Cursor handleGraphAnswer(@Nullable GraphAnswer<Integer> answer, boolean dataCouldChange,
                           @Nullable SelectionSnapshot previousSelection, @Nullable MouseEvent e) {
    if (dataCouldChange) {
      myTable.getModel().fireTableDataChanged();

      // since fireTableDataChanged clears selection we restore it here
      if (previousSelection != null) {
        previousSelection.restore(myTable.getVisibleGraph(),
                                  answer == null || (answer.getCommitToJump() != null && answer.doJump()),
                                  false);
      }
    }

    if (answer == null) {
      return null;
    }

    if (answer.isRepaintRequired()) myTable.repaint();
    if (answer.getCommitToJump() != null) {
      Integer row = myTable.getModel().getVisiblePack().getVisibleGraph().getVisibleRowIndex(answer.getCommitToJump());
      if (row != null && row >= 0 && answer.doJump()) {
        myTable.jumpToRow(row, true);
      }
      else if (e != null) {
        VcsLogUiUtil.showTooltip(myTable, new Point(e.getX() + 5, e.getY()), Balloon.Position.atRight,
                                 getArrowTooltipText(answer.getCommitToJump(), row));
      }
    }

    return answer.getCursorToSet();
  }

  private @NotNull @Nls String getArrowTooltipText(int commit, @Nullable Integer row) {
    VcsShortCommitDetails details;
    if (row != null && row >= 0) {
      details = myTable.getModel().getCommitMetadata(row, true); // preload rows around the commit
    }
    else {
      details = myLogData.getMiniDetailsGetter().getCommitData(commit, Collections.singletonList(commit)); // preload just the commit
    }

    if (details instanceof LoadingDetails) {
      CommitId commitId = myLogData.getCommitId(commit);
      if (commitId != null) {
        if (myLogData.getRoots().size() > 1) {
          return VcsLogBundle.message("vcs.log.graph.arrow.tooltip.jump.to.hash.in.root", commitId.getHash().toShortString(),
                                      commitId.getRoot().getName());
        }
        return VcsLogBundle.message("vcs.log.graph.arrow.tooltip.jump.to.hash", commitId.getHash().toShortString());
      }
      return "";
    }
    else {
      long time = details.getAuthorTime();
      String shortenedSubject = StringUtil.shortenTextWithEllipsis(details.getSubject(), 50, 0, "...");
      String commitMessage = HtmlChunk.text("\"" + shortenedSubject + "\"").bold().toString();
      return VcsLogBundle.message("vcs.log.graph.arrow.tooltip.jump.to.subject.author.date.time",
                                  commitMessage,
                                  CommitPresentationUtil.getAuthorPresentation(details),
                                  DateFormatUtil.formatDate(time),
                                  DateFormatUtil.formatTime(time));
    }
  }

  private boolean showTooltip(int row, @NotNull Point pointInCell, @NotNull Point point, boolean now) {
    JComponent tipComponent = getTooltip(myTable.getValueAt(row, myTable.getColumnViewIndex(Commit.INSTANCE)), pointInCell, row);

    if (tipComponent != null) {
      myTable.getExpandableItemsHandler().setEnabled(false);
      IdeTooltip tooltip = new IdeTooltip(myTable, point, new Wrapper(tipComponent)).setPreferredPosition(Balloon.Position.below);
      IdeTooltipManager.getInstance().show(tooltip, now);
      return true;
    }
    return false;
  }

  void showTooltip(int row) {
    Point topLeftCorner = new Point(myTable.getColumnDataRectLeft(myTable.getColumnViewIndex(Commit.INSTANCE)),
                                    row * myTable.getRowHeight());
    Point pointInCell = new Point(getTooltipXCoordinate(row), myTable.getRowHeight() / 2);
    showTooltip(row, pointInCell, new Point(topLeftCorner.x + pointInCell.x, topLeftCorner.y + pointInCell.y), true);
  }

  private static void triggerElementClick(@NotNull PrintElement printElement) {
    if (printElement instanceof NodePrintElement) {
      VcsLogUsageTriggerCollector.triggerClick("node");
    }
    else if (printElement instanceof EdgePrintElement) {
      if (((EdgePrintElement)printElement).hasArrow()) {
        VcsLogUsageTriggerCollector.triggerClick("arrow");
      }
    }
  }
}
