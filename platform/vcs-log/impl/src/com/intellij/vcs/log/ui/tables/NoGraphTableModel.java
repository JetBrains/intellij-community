package com.intellij.vcs.log.ui.tables;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.LoadMoreStage;
import com.intellij.vcs.log.data.RefsModel;
import com.intellij.vcs.log.graph.render.CommitCell;
import com.intellij.vcs.log.ui.VcsLogUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class NoGraphTableModel extends AbstractVcsLogTableModel<CommitCell, Hash> {

  private static final Logger LOG = Logger.getInstance(NoGraphTableModel.class);

  @NotNull private final VcsLogUI myUi;
  @NotNull private final List<VcsFullCommitDetails> myCommits;
  @NotNull private final RefsModel myRefsModel;
  @NotNull private final LoadMoreStage myLoadMoreStage;
  @NotNull private final AtomicBoolean myLoadMoreWasRequested = new AtomicBoolean();

  public NoGraphTableModel(@NotNull VcsLogUI UI, @NotNull List<VcsFullCommitDetails> commits, @NotNull RefsModel refsModel,
                           @NotNull LoadMoreStage loadMoreStage) {
    myUi = UI;
    myCommits = commits;
    myRefsModel = refsModel;
    myLoadMoreStage = loadMoreStage;
  }

  @Override
  public int getRowCount() {
    return myCommits.size();
  }

  @Nullable
  @Override
  protected VcsShortCommitDetails getShortDetails(int rowIndex) {
    return getFullCommitDetails(rowIndex);
  }

  @Nullable
  @Override
  public VcsFullCommitDetails getFullCommitDetails(int rowIndex) {
    VcsFullCommitDetails commits = myCommits.get(rowIndex);
    if (commits == null) {
      LOG.error("Couldn't identify details for commit at " + rowIndex, new Attachment("loaded_commits", myCommits.toString()));
    }
    return commits;
  }

  @Override
  public void requestToLoadMore(@NotNull Runnable onLoaded) {
    if (myLoadMoreWasRequested.compareAndSet(false, true)     // Don't send the request to VCS twice
        && myLoadMoreStage != LoadMoreStage.ALL_REQUESTED) {  // or when everything possible is loaded
      myUi.getTable().setPaintBusy(true);
      myUi.getFilterer().requestVcs(myUi.collectFilters(), myLoadMoreStage, onLoaded);
    }
  }

  @Override
  public boolean canRequestMore() {
    return myLoadMoreStage != LoadMoreStage.ALL_REQUESTED;
  }

  @Nullable
  @Override
  public List<Change> getSelectedChanges(@NotNull List<Integer> selectedRows) {
    List<Change> changes = new ArrayList<Change>();
    for (int selectedRow : selectedRows) {
      changes.addAll(myCommits.get(selectedRow).getChanges());
    }
    return changes;
  }

  @NotNull
  @Override
  public VirtualFile getRoot(int rowIndex) {
    VcsFullCommitDetails commit = myCommits.get(rowIndex);
    if (commit != null) {
      return commit.getRoot();
    }
    else {
      LOG.error("Couldn't identify root for commit at " + rowIndex, new Attachment("loaded_commits", myCommits.toString()));
      return FAKE_ROOT;
    }
  }

  @NotNull
  @Override
  protected CommitCell getCommitColumnCell(int index, @Nullable VcsShortCommitDetails details) {
    String subject = "";
    Collection<VcsRef> refs = Collections.emptyList();
    if (details != null) {
      subject = details.getSubject();
      refs = myRefsModel.refsToCommit(details.getHash());
    }
    return new CommitCell(subject, refs);
  }

  @NotNull
  @Override
  protected Class<CommitCell> getCommitColumnClass() {
    return CommitCell.class;
  }

  @Nullable
  @Override
  public Hash getHashAtRow(int row) {
    return myCommits.get(row).getHash();
  }

  @Override
  public int getRowOfCommit(@NotNull Hash hash) {
    for (int i = 0; i < myCommits.size(); i++) {
      VcsFullCommitDetails commit = myCommits.get(i);
      if (commit.getHash().equals(hash)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public int getRowOfCommitByPartOfHash(@NotNull String hash) {
    String lowercaseHash = hash.toLowerCase();
    for (int i = 0; i < myCommits.size(); i++) {
      VcsFullCommitDetails commit = myCommits.get(i);
      if (commit.getHash().toString().toLowerCase().startsWith(lowercaseHash)) {
        return i;
      }
    }
    return -1;
  }

}
