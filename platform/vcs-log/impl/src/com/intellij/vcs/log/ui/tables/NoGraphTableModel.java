package com.intellij.vcs.log.ui.tables;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.RefsModel;
import com.intellij.vcs.log.graph.render.CommitCell;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class NoGraphTableModel extends AbstractVcsLogTableModel<CommitCell> {

  private static final Logger LOG = Logger.getInstance(NoGraphTableModel.class);
  @NotNull private final List<Pair<VcsFullCommitDetails, VirtualFile>> myCommitsWithRoots;
  @NotNull private final RefsModel myRefsModel;

  public NoGraphTableModel(@NotNull List<Pair<VcsFullCommitDetails,VirtualFile>> commitsWithRoots, @NotNull RefsModel refsModel) {
    myCommitsWithRoots = commitsWithRoots;
    myRefsModel = refsModel;
  }

  @Override
  public int getRowCount() {
    return myCommitsWithRoots.size();
  }

  @Nullable
  @Override
  protected VcsShortCommitDetails getShortDetails(int rowIndex) {
    Pair<VcsFullCommitDetails, VirtualFile> commitAndRoot = myCommitsWithRoots.get(rowIndex);
    if (commitAndRoot != null) {
      return commitAndRoot.getFirst();
    }
    else {
      LOG.error("Couldn't identify details for commit at " + rowIndex, new Attachment("loaded_commits", myCommitsWithRoots.toString()));
      return null;
    }
  }

  @Nullable
  @Override
  public List<Change> getSelectedChanges(int[] selectedRows) {
    Arrays.sort(selectedRows);
    List<Change> changes = new ArrayList<Change>();
    for (int selectedRow : selectedRows) {
      changes.addAll(myCommitsWithRoots.get(selectedRow).getFirst().getChanges());
    }
    return changes;
  }

  @NotNull
  @Override
  protected VirtualFile getRoot(int rowIndex) {
    Pair<VcsFullCommitDetails, VirtualFile> commitAndRoot = myCommitsWithRoots.get(rowIndex);
    if (commitAndRoot != null) {
      return commitAndRoot.getSecond();
    }
    else {
      LOG.error("Couldn't identify root for commit at " + rowIndex, new Attachment("loaded_commits", myCommitsWithRoots.toString()));
      return UNKNOWN_ROOT;
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

}
