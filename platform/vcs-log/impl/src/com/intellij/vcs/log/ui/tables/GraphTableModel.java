package com.intellij.vcs.log.ui.tables;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.data.VisiblePack;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.render.GraphCommitCell;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.*;

public class GraphTableModel extends AbstractTableModel {

  private static final Logger LOG = Logger.getInstance(GraphTableModel.class);

  public static final int ROOT_COLUMN = 0;
  public static final int COMMIT_COLUMN = 1;
  public static final int AUTHOR_COLUMN = 2;
  public static final int DATE_COLUMN = 3;
  private static final int COLUMN_COUNT = DATE_COLUMN + 1;
  private static final String[] COLUMN_NAMES = {"", "Subject", "Author", "Date"};

  @NotNull protected final VcsLogUiImpl myUi;
  @NotNull private final VcsLogDataHolder myDataHolder;
  @NotNull private final VcsLogDataHolder myLogDataHolder;

  @NotNull protected VisiblePack myDataPack;

  private boolean myMoreRequested;

  public GraphTableModel(@NotNull VisiblePack dataPack, @NotNull VcsLogDataHolder dataHolder, @NotNull VcsLogUiImpl UI) {
    myLogDataHolder = dataHolder;
    myUi = UI;
    myDataPack = dataPack;
    myDataHolder = dataHolder;
  }

  @Override
  public int getRowCount() {
    return myDataPack.getVisibleGraph().getVisibleCommitCount();
  }

  @NotNull
  public VirtualFile getRoot(int rowIndex) {
    int head = myDataPack.getVisibleGraph().getRowInfo(rowIndex).getOneOfHeads();
    Collection<VcsRef> refs = myDataPack.getRefsModel().refsToCommit(head);
    if (refs.isEmpty()) {
      LOG.error("No references pointing to head " + myDataHolder.getHash(head) + " identified for commit at row " + rowIndex,
                new Attachment("details.txt", getErrorDetails()));
      // take the first root: it is the right choice in one-repo case, though it will likely fail in multi-repo case
      return myDataPack.getLogProviders().keySet().iterator().next();
    }
    return refs.iterator().next().getRoot();
  }

  @NotNull
  private String getErrorDetails() {
    StringBuilder sb = new StringBuilder();
    sb.append("LAST 100 COMMITS:\n");
    List<GraphCommit<Integer>> commits = myDataPack.getPermanentGraph().getAllCommits();
    for (int i = 0; i < 100 && i < commits.size(); i++) {
      GraphCommit<Integer> commit = commits.get(i);
      sb.append(String.format("%s -> %s\n", myDataHolder.getHash(commit.getId()).toShortString(), getParents(commit)));
    }
    sb.append("\nALL REFS:\n");
    printRefs(sb, myDataPack.getRefsModel().getAllRefsByRoot());
    return sb.toString();
  }

  @NotNull
  private String getParents(@NotNull GraphCommit<Integer> commit) {
    return StringUtil.join(commit.getParents(), new Function<Integer, String>() {
      @Override
      public String fun(Integer integer) {
        return myDataHolder.getHash(integer).toShortString();
      }
    }, ", ");
  }

  private static void printRefs(@NotNull StringBuilder sb, @NotNull Map<VirtualFile, Set<VcsRef>> refs) {
    for (Map.Entry<VirtualFile, Set<VcsRef>> entry : refs.entrySet()) {
      sb.append("\n\n" + entry.getKey().getName() + ":\n");
      sb.append(StringUtil.join(entry.getValue(), new Function<VcsRef, String>() {
        @Override
        public String fun(@NotNull VcsRef ref) {
          return ref.getName() + " : " + ref.getCommitHash().toShortString();
        }
      }, "\n"));
    }
  }

  @NotNull
  protected GraphCommitCell getCommitColumnCell(int rowIndex, @Nullable VcsShortCommitDetails details) {
    String message = "";
    List<VcsRef> refs = Collections.emptyList();
    if (details != null) {
      message = details.getSubject();
      refs = (List<VcsRef>)myDataPack.getRefsModel().refsToCommit(details.getId());
    }
    return new GraphCommitCell(message, refs);
  }

  @NotNull
  public Integer getCommitIdAtRow(int row) {
    return myDataPack.getVisibleGraph().getRowInfo(row).getCommit();
  }

  @Nullable
  public Hash getHashAtRow(int row) {
    return myDataHolder.getHash(getCommitIdAtRow(row));
  }

  public int getRowOfCommit(@NotNull final Hash hash) {
    final int commitIndex = myDataHolder.getCommitIndex(hash);
    return ContainerUtil.indexOf(VcsLogUtil.getVisibleCommits(myDataPack.getVisibleGraph()), new Condition<Integer>() {
      @Override
      public boolean value(Integer integer) {
        return integer == commitIndex;
      }
    });
  }

  public int getRowOfCommitByPartOfHash(@NotNull String partialHash) {
    Hash hash = myDataHolder.getHashMap().findHashByString(partialHash);
    return hash != null ? getRowOfCommit(hash) : -1;
  }

  @Override
  public final int getColumnCount() {
    return COLUMN_COUNT;
  }

  @Nullable
  private VcsShortCommitDetails getShortDetails(int rowIndex) {
    return myLogDataHolder.getMiniDetailsGetter().getCommitData(rowIndex, this);
  }

  @Nullable
  public VcsFullCommitDetails getFullCommitDetails(int rowIndex) {
    return myLogDataHolder.getCommitDetailsGetter().getCommitData(rowIndex, this);
  }

  /**
   * Requests the proper data provider to load more data from the log & recreate the model.
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
        if (data == null) {
          return "";
        }
        else {
          return data.getAuthor().getName() + (data.getAuthor().equals(data.getCommitter()) ? "" : "*");
        }
      case DATE_COLUMN:
        if (data == null || data.getAuthorTime() < 0) {
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

  /**
   * Returns Changes for commits at selected rows.<br/>
   * Rows are given in the order as they appear in the table, i. e. in reverse chronological order. <br/>
   * Changes can be returned as-is, i.e. with duplicate changes for a single file.
   * @return Changes selected in all rows, or null if this data is not ready yet.
   */
  @Nullable
  public List<Change> getSelectedChanges(@NotNull List<Integer> selectedRows) {
    List<Change> changes = new ArrayList<Change>();
    for (int row : selectedRows) {
      VcsFullCommitDetails commitData = getFullCommitDetails(row);
      if (commitData == null || commitData instanceof LoadingDetails) {
        return null;
      }
      changes.addAll(commitData.getChanges());
    }
    return changes;
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
}
