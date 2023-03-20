// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.RefsModel;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.table.column.VcsLogColumn;
import com.intellij.vcs.log.ui.table.column.VcsLogColumnManager;
import com.intellij.vcs.log.visible.VisiblePack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public final class GraphTableModel extends AbstractTableModel {
  private static final int UP_PRELOAD_COUNT = 20;
  private static final int DOWN_PRELOAD_COUNT = 40;

  private static final Logger LOG = Logger.getInstance(GraphTableModel.class);

  private final @NotNull VcsLogData myLogData;
  private final @NotNull Consumer<Runnable> myRequestMore;
  private final @NotNull VcsLogUiProperties myProperties;

  private @NotNull VisiblePack myDataPack = VisiblePack.EMPTY;

  private boolean myMoreRequested;

  public GraphTableModel(@NotNull VcsLogData logData,
                         @NotNull Consumer<Runnable> requestMore,
                         @NotNull VcsLogUiProperties properties) {
    myLogData = logData;
    myRequestMore = requestMore;
    myProperties = properties;
  }

  @Override
  public int getRowCount() {
    return myDataPack.getVisibleGraph().getVisibleCommitCount();
  }

  @Override
  public int getColumnCount() {
    return VcsLogColumnManager.getInstance().getModelColumnsCount();
  }

  @Override
  public String getColumnName(int column) {
    return getColumn(column).getLocalizedName();
  }

  @Override
  public @NotNull Object getValueAt(int rowIndex, int columnIndex) {
    return getValueAt(rowIndex, getColumn(columnIndex));
  }

  public @NotNull <T> T getValueAt(int rowIndex, @NotNull VcsLogColumn<T> column) {
    if (rowIndex >= getRowCount() - 1 && canRequestMore()) {
      requestToLoadMore(EmptyRunnable.INSTANCE);
    }

    try {
      return column.getValue(this, rowIndex);
    }
    catch (ProcessCanceledException ignore) {
      return column.getStubValue(this);
    }
    catch (Throwable t) {
      LOG.error("Failed to get information for the log table", t);
      return column.getStubValue(this);
    }
  }

  private static @NotNull VcsLogColumn<?> getColumn(int modelIndex) {
    return VcsLogColumnManager.getInstance().getColumn(modelIndex);
  }

  /**
   * Requests the proper data provider to load more data from the log & recreate the model.
   *
   * @param onLoaded will be called upon task completion on the EDT.
   */
  public void requestToLoadMore(@NotNull Runnable onLoaded) {
    myMoreRequested = true;
    myRequestMore.accept(onLoaded);
  }

  /**
   * Returns true if not all data has been loaded, i.e. there is sense to {@link #requestToLoadMore(Runnable) request more data}.
   */
  public boolean canRequestMore() {
    return !myMoreRequested && myDataPack.canRequestMore();
  }

  void setVisiblePack(@NotNull VisiblePack visiblePack) {
    myDataPack = visiblePack;
    myMoreRequested = false;
    fireTableDataChanged();
  }

  public @NotNull VisiblePack getVisiblePack() {
    return myDataPack;
  }

  public @NotNull VcsLogData getLogData() {
    return myLogData;
  }

  public @NotNull VcsLogUiProperties getProperties() {
    return myProperties;
  }

  public @NotNull Integer getIdAtRow(int row) {
    return myDataPack.getVisibleGraph().getRowInfo(row).getCommit();
  }

  public @NotNull VirtualFile getRootAtRow(int row) {
    return myDataPack.getRoot(row);
  }

  public @NotNull List<VcsRef> getRefsAtRow(int row) {
    return ((RefsModel)myDataPack.getRefs()).refsToCommit(getIdAtRow(row));
  }

  public @NotNull List<VcsRef> getBranchesAtRow(int row) {
    return ContainerUtil.filter(getRefsAtRow(row), ref -> ref.getType().isBranch());
  }

  public @NotNull VcsFullCommitDetails getFullDetails(int row) {
    Integer id = getIdAtRow(row);
    return myLogData.getCommitDetailsGetter().getCommitData(id);
  }

  public @NotNull VcsCommitMetadata getCommitMetadata(int row) {
    return getCommitMetadata(row, false);
  }

  public @NotNull VcsCommitMetadata getCommitMetadata(int row, boolean load) {
    Iterable<Integer> commitsToLoad = load ? getCommitsToLoad(row) : ContainerUtil.emptyList();
    return myLogData.getMiniDetailsGetter().getCommitData(getIdAtRow(row), commitsToLoad);
  }

  public @Nullable CommitId getCommitId(int row) {
    return myLogData.getCommitId(getIdAtRow(row));
  }

  public @NotNull VcsLogCommitSelection createSelection(int[] rows) {
    return new CommitSelectionImpl(myLogData, myDataPack.getVisibleGraph(), rows);
  }

  private @NotNull Iterable<Integer> getCommitsToLoad(int row) {
    int maxRows = getRowCount();
    return () -> new Iterator<>() {
      private int myRowIndex = Math.max(0, row - UP_PRELOAD_COUNT);

      @Override
      public boolean hasNext() {
        return myRowIndex < row + DOWN_PRELOAD_COUNT && myRowIndex < maxRows;
      }

      @Override
      public Integer next() {
        int nextRow = myRowIndex;
        myRowIndex++;
        return getIdAtRow(nextRow);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException("Removing elements is not supported.");
      }
    };
  }
}
