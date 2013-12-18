package com.intellij.vcs.log.ui.tables;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.AroundProvider;
import com.intellij.vcs.log.data.RefsModel;
import com.intellij.vcs.log.graph.render.CommitCell;
import com.intellij.vcs.log.ui.VcsLogUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class NoGraphTableModel extends AbstractVcsLogTableModel<CommitCell, Hash> {

  private static final Logger LOG = Logger.getInstance(NoGraphTableModel.class);

  @NotNull private final VcsLogUI myUi;
  @NotNull private final List<VcsFullCommitDetails> myCommits;
  @NotNull private final RefsModel myRefsModel;
  @NotNull private final AroundProvider<Hash> myAroundProvider;
  private boolean myAllowLoadingMoreRequest;

  public NoGraphTableModel(@NotNull VcsLogUI UI, @NotNull List<VcsFullCommitDetails> commits, @NotNull RefsModel refsModel,
                           boolean allowLoadingMoreRequest) {
    myUi = UI;
    myCommits = commits;
    myRefsModel = refsModel;
    myAllowLoadingMoreRequest = allowLoadingMoreRequest;
    myAroundProvider = new HashAroundProvider();
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
  public void requestToLoadMore() {
    if (!myAllowLoadingMoreRequest) {
      return;
    }

    myUi.getTable().setPaintBusy(true);
    myUi.getFilterer().requestVcs(myUi.collectFilters(), new Runnable() {
      @Override
      public void run() {
        myUi.getTable().setPaintBusy(false);
      }
    });
    myAllowLoadingMoreRequest = false; // Don't send the request to VCS twice
  }

  @Nullable
  @Override
  public List<Change> getSelectedChanges(int[] selectedRows) {
    Arrays.sort(selectedRows);
    List<Change> changes = new ArrayList<Change>();
    for (int selectedRow : selectedRows) {
      changes.addAll(myCommits.get(selectedRow).getChanges());
    }
    return changes;
  }

  @NotNull
  @Override
  protected VirtualFile getRoot(int rowIndex) {
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

  @Nullable
  @Override
  public Hash getCommit(int row) {
    return getHashAtRow(row);
  }

  @NotNull
  @Override
  public AroundProvider<Hash> getAroundProvider() {
    return myAroundProvider;
  }

  private class HashAroundProvider implements AroundProvider<Hash> {
    @NotNull
    @Override
    public MultiMap<VirtualFile, Hash> getCommitsAround(@NotNull Hash selectedHash, int above, int below) {
      int rowIndex = findRowIndex(selectedHash);
      if (rowIndex < 0) {
        LOG.error("Couldn't find the hash " + selectedHash + " among supplied commits",
                  new Attachment("filtered_commits.txt", myCommits.toString()));
        return MultiMap.emptyInstance();
      }

      MultiMap<VirtualFile, Hash> commits = MultiMap.create();
      for (int i = Math.max(0, rowIndex - above); i < rowIndex + below && i < myCommits.size(); i++) {
        VcsFullCommitDetails details = myCommits.get(i);
        commits.putValue(details.getRoot(), details.getHash());
      }
      return commits;
    }

    private int findRowIndex(final Hash hash) {
      return ContainerUtil.indexOf(myCommits, new Condition<VcsFullCommitDetails>() {
        @Override
        public boolean value(VcsFullCommitDetails details) {
          return details.getHash().equals(hash);
        }
      });
    }

    @NotNull
    @Override
    public Hash resolveId(@NotNull Hash hash) {
      return hash;
    }
  }
}
