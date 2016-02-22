package com.intellij.vcs.log.ui.tables;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.CommitIdByStringCondition;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsShortCommitDetails;
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

  public GraphTableModel(@NotNull VisiblePack dataPack, @NotNull VcsLogDataHolder dataHolder, @NotNull VcsLogUiImpl ui) {
    myLogDataHolder = dataHolder;
    myUi = ui;
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
      LOG.error("No references pointing to head " + myDataHolder.getCommitId(head) + " identified for commit at row " + rowIndex,
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
      sb.append(String.format("%s -> %s\n", myDataHolder.getCommitId(commit.getId()).getHash().toShortString(), getParents(commit)));
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
        return myDataHolder.getCommitId(integer).getHash().toShortString();
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
      refs = (List<VcsRef>)myDataPack.getRefsModel().refsToCommit(details.getId(), details.getRoot());
    }
    return new GraphCommitCell(message, refs);
  }

  @NotNull
  public Integer getIdAtRow(int row) {
    return myDataPack.getVisibleGraph().getRowInfo(row).getCommit();
  }

  @NotNull
  public CommitId getCommitIdAtRow(int row) {
    return myDataHolder.getCommitId(getIdAtRow(row));
  }

  public int getRowOfCommit(@NotNull final Hash hash, @NotNull VirtualFile root) {
    final int commitIndex = myDataHolder.getCommitIndex(hash, root);
    return ContainerUtil.indexOf(VcsLogUtil.getVisibleCommits(myDataPack.getVisibleGraph()), new Condition<Integer>() {
      @Override
      public boolean value(Integer integer) {
        return integer == commitIndex;
      }
    });
  }

  public int getRowOfCommitByPartOfHash(@NotNull String partialHash) {
    final CommitIdByStringCondition hashByString = new CommitIdByStringCondition(partialHash);
    CommitId commitId = myDataHolder.getHashMap().findCommitId(new Condition<CommitId>() {
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

    VcsShortCommitDetails data = myLogDataHolder.getMiniDetailsGetter().getCommitData(rowIndex, this);
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
}
