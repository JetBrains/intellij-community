package com.intellij.vcs.log.ui.table;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.exception.FrequentErrorLogger;
import com.intellij.util.text.JBDateFormat;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.CommitIdByStringCondition;
import com.intellij.vcs.log.data.RefsModel;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.CommonUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil;
import com.intellij.vcs.log.ui.render.GraphCommitCell;
import com.intellij.vcs.log.visible.VisiblePack;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.AbstractList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

/**
 * Columns correspond exactly to {@link VcsLogColumn} enum
 */
public class GraphTableModel extends AbstractTableModel {
  private static final int UP_PRELOAD_COUNT = 20;
  private static final int DOWN_PRELOAD_COUNT = 40;

  public static final int COMMIT_NOT_FOUND = -1;
  public static final int COMMIT_DOES_NOT_MATCH = -2;

  private static final Logger LOG = Logger.getInstance(GraphTableModel.class);
  private static final FrequentErrorLogger ERROR_LOG = FrequentErrorLogger.newInstance(LOG);

  @NotNull private final VcsLogData myLogData;
  @NotNull private final Consumer<? super Runnable> myRequestMore;
  @NotNull private final VcsLogUiProperties myProperties;

  @NotNull private VisiblePack myDataPack = VisiblePack.EMPTY;

  private boolean myMoreRequested;

  public GraphTableModel(@NotNull VcsLogData logData,
                         @NotNull Consumer<? super Runnable> requestMore,
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
  public final int getColumnCount() {
    return VcsLogColumn.count();
  }

  @Override
  public Class<?> getColumnClass(int column) {
    return VcsLogColumn.fromOrdinal(column).getContentClass();
  }

  @Override
  public String getColumnName(int column) {
    return VcsLogColumn.fromOrdinal(column).getName();
  }

  public int getRowOfCommit(@NotNull Hash hash, @NotNull VirtualFile root) {
    if (!myLogData.getStorage().containsCommit(new CommitId(hash, root))) return COMMIT_NOT_FOUND;
    return getRowOfCommitWithoutCheck(hash, root);
  }

  public int getRowOfCommitByPartOfHash(@NotNull String partialHash) {
    CommitIdByStringCondition hashByString = new CommitIdByStringCondition(partialHash);
    Ref<Boolean> commitExists = new Ref<>(false);
    CommitId commitId = myLogData.getStorage().findCommitId(
      commitId1 -> {
        if (hashByString.value(commitId1)) {
          commitExists.set(true);
          return getRowOfCommitWithoutCheck(commitId1.getHash(), commitId1.getRoot()) >= 0;
        }
        return false;
      });
    return commitId != null
           ? getRowOfCommitWithoutCheck(commitId.getHash(), commitId.getRoot())
           : (commitExists.get() ? COMMIT_DOES_NOT_MATCH : COMMIT_NOT_FOUND);
  }

  private int getRowOfCommitWithoutCheck(@NotNull Hash hash, @NotNull VirtualFile root) {
    int commitIndex = myLogData.getCommitIndex(hash, root);
    Integer rowIndex = myDataPack.getVisibleGraph().getVisibleRowIndex(commitIndex);
    return rowIndex == null ? COMMIT_DOES_NOT_MATCH : rowIndex;
  }

  @NotNull
  @Override
  public final Object getValueAt(int rowIndex, int columnIndex) {
    return getValueAt(rowIndex, VcsLogColumn.fromOrdinal(columnIndex));
  }

  @NotNull
  public final Object getValueAt(int rowIndex, @NotNull VcsLogColumn column) {
    if (rowIndex >= getRowCount() - 1 && canRequestMore()) {
      requestToLoadMore(EmptyRunnable.INSTANCE);
    }

    VcsShortCommitDetails data = getCommitMetadata(rowIndex);
    switch (column) {
      case ROOT:
        return getRootSafely(rowIndex);
      case COMMIT:
        return getCommitCellSafely(rowIndex, data);
      case AUTHOR:
        return getAuthorSafely(data);
      case DATE:
        return getDateSafely(data);
      case HASH:
        return getHashSafely(data);
      default:
        throw new IllegalStateException("Unexpected value: " + column);
    }
  }

  @NotNull
  private FilePath getRootSafely(int rowIndex) {
    return getOrLogAndReturnStub(() -> myDataPack.getFilePath(rowIndex), VcsUtil.getFilePath(getFirstItem(myLogData.getRoots())));
  }

  @NotNull
  private GraphCommitCell getCommitCellSafely(int rowIndex, @NotNull VcsShortCommitDetails data) {
    return getOrLogAndReturnStub(() -> {
      return new GraphCommitCell(data.getSubject(), getRefsAtRow(rowIndex),
                                 myDataPack.getVisibleGraph().getRowInfo(rowIndex).getPrintElements());
    }, new GraphCommitCell("", Collections.emptyList(), Collections.emptyList()));
  }

  @NotNull
  private static String getAuthorSafely(@NotNull VcsShortCommitDetails data) {
    return getOrLogAndReturnStub(() -> CommitPresentationUtil.getAuthorPresentation(data), "");
  }

  @NotNull
  private String getDateSafely(@NotNull VcsShortCommitDetails data) {
    return getOrLogAndReturnStub(() -> {
      long timeStamp = myProperties.exists(CommonUiProperties.PREFER_COMMIT_DATE) &&
                       Boolean.TRUE.equals(myProperties.get(CommonUiProperties.PREFER_COMMIT_DATE)) ?
                       data.getCommitTime() : data.getAuthorTime();
      return timeStamp < 0 ? "" : JBDateFormat.getFormatter().formatPrettyDateTime(timeStamp);
    }, "");
  }

  @NotNull
  private static String getHashSafely(@NotNull VcsShortCommitDetails data) {
    return getOrLogAndReturnStub(() -> data.getId().toShortString(), "");
  }

  @NotNull
  private static <T> T getOrLogAndReturnStub(@NotNull Computable<T> computable, @NotNull T stub) {
    try {
      return computable.compute();
    }
    catch (ProcessCanceledException ignore) {
      return stub;
    }
    catch (Throwable t) {
      ERROR_LOG.error("Failed to get information for the log table", t);
      return stub;
    }
  }

  /**
   * Requests the proper data provider to load more data from the log & recreate the model.
   *
   * @param onLoaded will be called upon task completion on the EDT.
   */
  public void requestToLoadMore(@NotNull Runnable onLoaded) {
    myMoreRequested = true;
    myRequestMore.consume(onLoaded);
  }

  /**
   * Returns true if not all data has been loaded, i.e. there is sense to {@link #requestToLoadMore(Runnable) request more data}.
   */
  public boolean canRequestMore() {
    return !myMoreRequested && myDataPack.canRequestMore();
  }

  public void setVisiblePack(@NotNull VisiblePack visiblePack) {
    myDataPack = visiblePack;
    myMoreRequested = false;
    fireTableDataChanged();
  }

  @NotNull
  public VisiblePack getVisiblePack() {
    return myDataPack;
  }

  @NotNull
  public Integer getIdAtRow(int row) {
    return myDataPack.getVisibleGraph().getRowInfo(row).getCommit();
  }

  @NotNull
  public VirtualFile getRootAtRow(int row) {
    return myDataPack.getRoot(row);
  }

  @NotNull
  public List<VcsRef> getRefsAtRow(int row) {
    return ((RefsModel)myDataPack.getRefs()).refsToCommit(getIdAtRow(row));
  }

  @NotNull
  public List<VcsRef> getBranchesAtRow(int row) {
    return ContainerUtil.filter(getRefsAtRow(row), ref -> ref.getType().isBranch());
  }

  @NotNull
  public VcsFullCommitDetails getFullDetails(int row) {
    Integer id = getIdAtRow(row);
    return myLogData.getCommitDetailsGetter().getCommitData(id, Collections.singleton(id));
  }

  @NotNull
  public VcsCommitMetadata getCommitMetadata(int row) {
    return myLogData.getMiniDetailsGetter().getCommitData(getIdAtRow(row), getCommitsToPreload(row));
  }

  @Nullable
  public CommitId getCommitId(int row) {
    return myLogData.getCommitId(getIdAtRow(row));
  }

  @NotNull
  public List<VcsFullCommitDetails> getFullDetails(int[] rows) {
    return getDataForRows(rows, this::getFullDetails);
  }

  @NotNull
  public List<VcsCommitMetadata> getCommitMetadata(int[] rows) {
    return getDataForRows(rows, this::getCommitMetadata);
  }

  @NotNull
  public List<CommitId> getCommitIds(int[] rows) {
    return getDataForRows(rows, this::getCommitId);
  }

  @NotNull
  public List<Integer> convertToCommitIds(@NotNull List<Integer> rows) {
    return ContainerUtil.map(rows, (NotNullFunction<Integer, Integer>)this::getIdAtRow);
  }

  @NotNull
  private Iterable<Integer> getCommitsToPreload(int row) {
    int maxRows = getRowCount();
    return () -> new Iterator<Integer>() {
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

  @NotNull
  private static <T> List<T> getDataForRows(int[] rows, @NotNull Function<? super Integer, ? extends T> dataGetter) {
    return new AbstractList<T>() {
      @NotNull
      @Override
      public T get(int index) {
        return dataGetter.apply(rows[index]);
      }

      @Override
      public int size() {
        return rows.length;
      }
    };
  }
}
