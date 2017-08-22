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

import com.google.common.primitives.Ints;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ScrollingUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.VisibleGraph;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

class Selection {
  @NotNull private final VcsLogGraphTable myTable;
  @NotNull private final TIntHashSet mySelectedCommits;
  @Nullable private final Integer myVisibleSelectedCommit;
  @Nullable private final Integer myDelta;
  private final boolean myIsOnTop;

  public Selection(@NotNull VcsLogGraphTable table) {
    myTable = table;
    List<Integer> selectedRows = ContainerUtil.sorted(Ints.asList(myTable.getSelectedRows()));
    Couple<Integer> visibleRows = ScrollingUtil.getVisibleRows(myTable);
    myIsOnTop = visibleRows.first - 1 == 0;

    VisibleGraph<Integer> graph = myTable.getVisibleGraph();

    mySelectedCommits = new TIntHashSet();

    Integer visibleSelectedCommit = null;
    Integer delta = null;
    for (int row : selectedRows) {
      if (row < graph.getVisibleCommitCount()) {
        Integer commit = graph.getRowInfo(row).getCommit();
        mySelectedCommits.add(commit);
        if (visibleRows.first - 1 <= row && row <= visibleRows.second && visibleSelectedCommit == null) {
          visibleSelectedCommit = commit;
          delta = myTable.getCellRect(row, 0, false).y - myTable.getVisibleRect().y;
        }
      }
    }
    if (visibleSelectedCommit == null && visibleRows.first - 1 >= 0) {
      visibleSelectedCommit = graph.getRowInfo(visibleRows.first - 1).getCommit();
      delta = myTable.getCellRect(visibleRows.first - 1, 0, false).y - myTable.getVisibleRect().y;
    }

    myVisibleSelectedCommit = visibleSelectedCommit;
    myDelta = delta;
  }

  public void restore(@NotNull VisibleGraph<Integer> newVisibleGraph, boolean scrollToSelection, boolean permGraphChanged) {
    Pair<TIntHashSet, Integer> toSelectAndScroll = findRowsToSelectAndScroll(myTable.getModel(), newVisibleGraph);
    if (!toSelectAndScroll.first.isEmpty()) {
      myTable.getSelectionModel().setValueIsAdjusting(true);
      toSelectAndScroll.first.forEach(row -> {
        myTable.addRowSelectionInterval(row, row);
        return true;
      });
      myTable.getSelectionModel().setValueIsAdjusting(false);
    }
    if (scrollToSelection) {
      if (myIsOnTop && permGraphChanged) { // scroll on top when some fresh commits arrive
        scrollToRow(0, 0);
      }
      else if (toSelectAndScroll.second != null) {
        assert myDelta != null;
        scrollToRow(toSelectAndScroll.second, myDelta);
      }
    }
    // sometimes commits that were selected are now collapsed
    // currently in this case selection disappears
    // in the future we need to create a method in LinearGraphController that allows to calculate visible commit for our commit
    // or answer from collapse action could return a map that gives us some information about what commits were collapsed and where
  }

  private void scrollToRow(Integer row, Integer delta) {
    Rectangle startRect = myTable.getCellRect(row, 0, true);
    myTable.scrollRectToVisible(new Rectangle(startRect.x, Math.max(startRect.y - delta, 0),
                                              startRect.width, myTable.getVisibleRect().height));
  }

  @NotNull
  private Pair<TIntHashSet, Integer> findRowsToSelectAndScroll(@NotNull GraphTableModel model,
                                                               @NotNull VisibleGraph<Integer> visibleGraph) {
    TIntHashSet rowsToSelect = new TIntHashSet();

    if (model.getRowCount() == 0) {
      // this should have been covered by facade.getVisibleCommitCount,
      // but if the table is empty (no commits match the filter), the GraphFacade is not updated, because it can't handle it
      // => it has previous values set.
      return Pair.create(rowsToSelect, null);
    }

    Integer rowToScroll = null;
    for (int row = 0;
         row < visibleGraph.getVisibleCommitCount() && (rowsToSelect.size() < mySelectedCommits.size() || rowToScroll == null);
         row++) { //stop iterating if found all hashes
      int commit = visibleGraph.getRowInfo(row).getCommit();
      if (mySelectedCommits.contains(commit)) {
        rowsToSelect.add(row);
      }
      if (myVisibleSelectedCommit != null && myVisibleSelectedCommit == commit) {
        rowToScroll = row;
      }
    }
    return Pair.create(rowsToSelect, rowToScroll);
  }
}
