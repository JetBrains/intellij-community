package com.intellij.vcs.log.ui.tables;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.CommitIdByStringCondition;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.VcsLogDataManager;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.data.*;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.render.GraphCommitCell;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class GraphTableModel extends AbstractTableModel {
  public static final int ROOT_COLUMN = 0;
  public static final int COMMIT_COLUMN = 1;
  public static final int AUTHOR_COLUMN = 2;
  public static final int DATE_COLUMN = 3;
  private static final int COLUMN_COUNT = DATE_COLUMN + 1;
  private static final String[] COLUMN_NAMES = {"", "Subject", "Author", "Date"};

  private static final int UP_PRELOAD_COUNT = 20;
  private static final int DOWN_PRELOAD_COUNT = 40;

  @NotNull private final VcsLogDataManager myLogDataManager;
  @NotNull protected final VcsLogUiImpl myUi;

  @NotNull protected VisiblePack myDataPack;

  private boolean myMoreRequested;

  public GraphTableModel(@NotNull VisiblePack dataPack, @NotNull VcsLogDataManager dataManager, @NotNull VcsLogUiImpl ui) {
    myLogDataManager = dataManager;
    myUi = ui;
    myDataPack = dataPack;
  }

  @Override
  public int getRowCount() {
    return myDataPack.getVisibleGraph().getVisibleCommitCount();
  }

  @NotNull
  public VirtualFile getRoot(int rowIndex) {
    return myDataPack.getRoot(rowIndex);
  }

  @NotNull
  protected GraphCommitCell getCommitColumnCell(int rowIndex, @Nullable VcsShortCommitDetails details) {
    String message = "";
    List<VcsRef> refs = Collections.emptyList();
    if (details != null) {
      message = details.getSubject();
      refs = (List<VcsRef>)myDataPack.getRefs().refsToCommit(details.getId(), details.getRoot());
    }
    return new GraphCommitCell(message, refs);
  }

  @NotNull
  public Integer getIdAtRow(int row) {
    return myDataPack.getVisibleGraph().getRowInfo(row).getCommit();
  }

  @NotNull
  public CommitId getCommitIdAtRow(int row) {
    return myLogDataManager.getCommitId(getIdAtRow(row));
  }

  public int getRowOfCommit(@NotNull final Hash hash, @NotNull VirtualFile root) {
    final int commitIndex = myLogDataManager.getCommitIndex(hash, root);
    return ContainerUtil.indexOf(VcsLogUtil.getVisibleCommits(myDataPack.getVisibleGraph()), new Condition<Integer>() {
      @Override
      public boolean value(Integer integer) {
        return integer == commitIndex;
      }
    });
  }

  public int getRowOfCommitByPartOfHash(@NotNull String partialHash) {
    final CommitIdByStringCondition hashByString = new CommitIdByStringCondition(partialHash);
    CommitId commitId = myLogDataManager.getHashMap().findCommitId(new Condition<CommitId>() {
      @Override
      public boolean value(CommitId commitId) {
        return hashByString.value(commitId) && getRowOfCommit(commitId.getHash(), commitId.getRoot()) != -1;
      }
    });
    return commitId != null ? getRowOfCommit(commitId.getHash(), commitId.getRoot()) : -1;
  }

  @Override
  public final int getColumnCount() {
    return COLUMN_COUNT;
  }

  /**
   * Requests the proper data provider to load more data from the log & recreate the model.
   *
   * @param onLoaded will be called upon task completion on the EDT.
   */
  public void requestToLoadMore(@NotNull Runnable onLoaded) {
    myMoreRequested = true;
    myUi.getFilterer().moreCommitsNeeded(onLoaded);
    myUi.getTable().setPaintBusy(true);
  }

  @NotNull
  @Override
  public final Object getValueAt(int rowIndex, int columnIndex) {
    if (rowIndex >= getRowCount() - 1 && canRequestMore()) {
      requestToLoadMore(EmptyRunnable.INSTANCE);
    }

    VcsShortCommitDetails data = getShortDetails(rowIndex);
    switch (columnIndex) {
      case ROOT_COLUMN:
        return getRoot(rowIndex);
      case COMMIT_COLUMN:
        return getCommitColumnCell(rowIndex, data);
      case AUTHOR_COLUMN:
        String authorString = data.getAuthor().getName();
        if (authorString.isEmpty()) authorString = data.getAuthor().getEmail();
        return authorString + (data.getAuthor().equals(data.getCommitter()) ? "" : "*");
      case DATE_COLUMN:
        if (data.getAuthorTime() < 0) {
          return "";
        }
        else {
          return DateFormatUtil.formatDateTime(data.getAuthorTime());
        }
      default:
        throw new IllegalArgumentException("columnIndex is " + columnIndex + " > " + (COLUMN_COUNT - 1));
    }
  }

  /**
   * Returns true if not all data has been loaded, i.e. there is sense to {@link #requestToLoadMore(Runnable) request more data}.
   */
  public boolean canRequestMore() {
    return !myMoreRequested && myDataPack.canRequestMore();
  }

  @Override
  public Class<?> getColumnClass(int column) {
    switch (column) {
      case ROOT_COLUMN:
        return VirtualFile.class;
      case COMMIT_COLUMN:
        return GraphCommitCell.class;
      case AUTHOR_COLUMN:
        return String.class;
      case DATE_COLUMN:
        return String.class;
      default:
        throw new IllegalArgumentException("columnIndex is " + column + " > " + (COLUMN_COUNT - 1));
    }
  }

  @Override
  public String getColumnName(int column) {
    return COLUMN_NAMES[column];
  }

  public void setVisiblePack(@NotNull VisiblePack visiblePack) {
    myDataPack = visiblePack;
    myMoreRequested = false;
    fireTableDataChanged();
  }

  public VisiblePack getVisiblePack() {
    return myDataPack;
  }

  @NotNull
  public VcsFullCommitDetails getFullDetails(int row) {
    return getDetails(row, myLogDataManager.getCommitDetailsGetter());
  }

  @NotNull
  public VcsShortCommitDetails getShortDetails(int row) {
    return getDetails(row, myLogDataManager.getMiniDetailsGetter());
  }

  @NotNull
  private <T extends VcsShortCommitDetails> T getDetails(int row, DataGetter<T> dataGetter) {
    Iterable<Integer> iterable = createRowsIterable(row, UP_PRELOAD_COUNT, DOWN_PRELOAD_COUNT, getRowCount());
    return dataGetter.getCommitData(getIdAtRow(row), iterable);
  }

  private Iterable<Integer> createRowsIterable(final int row, final int above, final int below, final int maxRows) {
    return new Iterable<Integer>() {
      @NotNull
      @Override
      public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
          private int myRowIndex = Math.max(0, row - above);

          @Override
          public boolean hasNext() {
            return myRowIndex < row + below && myRowIndex < maxRows;
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
    };
  }

  @NotNull
  public List<Integer> convertToHashesAndRoots(@NotNull List<Integer> rows) {
    return ContainerUtil.map(rows, new NotNullFunction<Integer, Integer>() {
      @NotNull
      @Override
      public Integer fun(Integer row) {
        return getIdAtRow(row);
      }
    });
  }
}
