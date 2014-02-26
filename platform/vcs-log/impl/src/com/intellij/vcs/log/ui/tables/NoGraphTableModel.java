package com.intellij.vcs.log.ui.tables;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.LoadMoreStage;
import com.intellij.vcs.log.data.VcsLogDataHolder;
import com.intellij.vcs.log.graph.render.CommitCell;
import com.intellij.vcs.log.ui.VcsLogUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class NoGraphTableModel extends AbstractVcsLogTableModel<CommitCell, Hash> {

  private static final Logger LOG = Logger.getInstance(NoGraphTableModel.class);

  @NotNull private final DataPack myDataPack;
  @NotNull private final VcsLogUI myUi;
  @NotNull private final List<Pair<Hash, VirtualFile>> myCommitsWithRoots;
  @NotNull private final LoadMoreStage myLoadMoreStage;
  @NotNull private final AtomicBoolean myLoadMoreWasRequested = new AtomicBoolean();

  public NoGraphTableModel(@NotNull DataPack dataPack, @NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUI UI,
                           @NotNull List<Pair<Hash, VirtualFile>> commitsWithRoots, @NotNull LoadMoreStage loadMoreStage) {
    super(logDataHolder);
    myDataPack = dataPack;
    myUi = UI;
    myCommitsWithRoots = commitsWithRoots;
    myLoadMoreStage = loadMoreStage;
  }

  @Override
  public int getRowCount() {
    return myCommitsWithRoots.size();
  }

  @Override
  public void requestToLoadMore(@NotNull Runnable onLoaded) {
    if (myLoadMoreWasRequested.compareAndSet(false, true)     // Don't send the request to VCS twice
        && myLoadMoreStage != LoadMoreStage.ALL_REQUESTED) {  // or when everything possible is loaded
      myUi.getTable().setPaintBusy(true);
      myUi.getFilterer().requestVcs(myDataPack, myUi.collectFilters(), myLoadMoreStage, onLoaded);
    }
  }

  @Override
  public boolean canRequestMore() {
    return myLoadMoreStage != LoadMoreStage.ALL_REQUESTED;
  }

  @NotNull
  @Override
  public VirtualFile getRoot(int rowIndex) {
    Pair<Hash, VirtualFile> commit = myCommitsWithRoots.get(rowIndex);
    if (commit != null) {
      return commit.getSecond();
    }
    else {
      LOG.error("Couldn't identify root for commit at " + rowIndex, new Attachment("loaded_commits", myCommitsWithRoots.toString()));
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
      refs = myDataPack.getRefsModel().refsToCommit(details.getHash());
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
    return myCommitsWithRoots.get(row).getFirst();
  }

  @Override
  public int getRowOfCommit(@NotNull Hash hash) {
    for (int i = 0; i < myCommitsWithRoots.size(); i++) {
      if (hash.equals(myCommitsWithRoots.get(i).getFirst())) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public int getRowOfCommitByPartOfHash(@NotNull String hash) {
    String lowercaseHash = hash.toLowerCase();
    for (int i = 0; i < myCommitsWithRoots.size(); i++) {
      Hash commit = myCommitsWithRoots.get(i).getFirst();
      if (commit.toString().toLowerCase().startsWith(lowercaseHash)) {
        return i;
      }
    }
    return -1;
  }

}
