// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.EmptyConsumer;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.CommitIdByStringCondition;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.intellij.vcs.log.ui.table.GraphTableModel.COMMIT_DOES_NOT_MATCH;
import static com.intellij.vcs.log.ui.table.GraphTableModel.COMMIT_NOT_FOUND;

public class VcsLogImpl implements VcsLog {
  @NotNull private final VcsLogData myLogData;
  @NotNull private final AbstractVcsLogUi myUi;

  public VcsLogImpl(@NotNull VcsLogData manager, @NotNull AbstractVcsLogUi ui) {
    myLogData = manager;
    myUi = ui;
  }

  @Override
  @NotNull
  public List<CommitId> getSelectedCommits() {
    return myUi.getTable().getModel().getCommitIds(myUi.getTable().getSelectedRows());
  }

  @NotNull
  @Override
  public List<VcsCommitMetadata> getSelectedShortDetails() {
    return myUi.getTable().getModel().getCommitMetadata(myUi.getTable().getSelectedRows());
  }

  @NotNull
  @Override
  public List<VcsFullCommitDetails> getSelectedDetails() {
    return myUi.getTable().getModel().getFullDetails(myUi.getTable().getSelectedRows());
  }

  @Override
  public void requestSelectedDetails(@NotNull Consumer<? super List<VcsFullCommitDetails>> consumer) {
    List<Integer> rowsList = Ints.asList(myUi.getTable().getSelectedRows());
    myLogData.getCommitDetailsGetter().loadCommitsData(getTable().getModel().convertToCommitIds(rowsList), consumer,
                                                       EmptyConsumer.getInstance(), null);
  }

  @Nullable
  @Override
  public Collection<String> getContainingBranches(@NotNull Hash commitHash, @NotNull VirtualFile root) {
    return myLogData.getContainingBranchesGetter().getContainingBranchesQuickly(root, commitHash);
  }

  @NotNull
  @Override
  public ListenableFuture<Boolean> jumpToReference(final @NotNull String reference, boolean focus) {
    if (StringUtil.isEmptyOrSpaces(reference)) return Futures.immediateFuture(false);

    SettableFuture<Boolean> future = SettableFuture.create();
    VcsLogRefs refs = myUi.getDataPack().getRefs();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      List<VcsRef> matchingRefs = refs.stream().filter(ref -> ref.getName().startsWith(reference)).collect(Collectors.toList());
      ApplicationManager.getApplication().invokeLater(() -> {
        if (matchingRefs.isEmpty()) {
          future.setFuture(jumpToHash(reference, focus));
        }
        else {
          VcsRef ref = Collections.min(matchingRefs, new VcsGoToRefComparator(myUi.getDataPack().getLogProviders()));
          future.setFuture(jumpToCommit(ref.getCommitHash(), ref.getRoot(), focus));
        }
      });
    });
    return future;
  }

  @Override
  @NotNull
  public ListenableFuture<Boolean> jumpToCommit(@NotNull Hash commitHash, @NotNull VirtualFile root, boolean focus) {
    SettableFuture<Boolean> future = SettableFuture.create();
    myUi.jumpTo(commitHash, (visiblePack, hash) -> {
      if (!myLogData.getStorage().containsCommit(new CommitId(hash, root))) return COMMIT_NOT_FOUND;
      return getCommitRow(visiblePack, hash, root);
    }, future, false, focus);
    return future;
  }

  @NotNull
  private ListenableFuture<Boolean> jumpToHash(@NotNull String commitHash, boolean focus) {
    SettableFuture<Boolean> future = SettableFuture.create();
    String trimmed = StringUtil.trim(commitHash, ch -> !StringUtil.containsChar("()'\"`", ch));
    if (!VcsLogUtil.HASH_REGEX.matcher(trimmed).matches()) {
      VcsBalloonProblemNotifier.showOverChangesView(myUi.getLogData().getProject(),
                                                    VcsLogBundle.message("vcs.log.commit.or.reference.not.found", commitHash),
                                                    MessageType.WARNING);
      future.set(false);
      return future;
    }
    myUi.jumpTo(trimmed, this::getCommitRow, future, false, focus);
    return future;
  }

  private int getCommitRow(@NotNull VisiblePack visiblePack, @NotNull String partialHash) {
    if (partialHash.length() == VcsLogUtil.FULL_HASH_LENGTH) {
      int row = COMMIT_NOT_FOUND;
      Hash candidateHash = HashImpl.build(partialHash);
      for (VirtualFile candidateRoot : myLogData.getRoots()) {
        if (myLogData.getStorage().containsCommit(new CommitId(candidateHash, candidateRoot))) {
          int candidateRow = getCommitRow(visiblePack, candidateHash, candidateRoot);
          if (candidateRow >= 0) return candidateRow;
          if (row == COMMIT_NOT_FOUND) row = candidateRow;
        }
      }
      return row;
    }

    IntRef row = new IntRef(COMMIT_NOT_FOUND);
    myLogData.getStorage().iterateCommits(candidate -> {
      if (CommitIdByStringCondition.matches(candidate, partialHash)) {
        int candidateRow = getCommitRow(visiblePack, candidate.getHash(), candidate.getRoot());
        if (row.get() == COMMIT_NOT_FOUND) row.set(candidateRow);
        return candidateRow < 0;
      }
      return true;
    });
    return row.get();
  }

  private int getCommitRow(@NotNull VisiblePack visiblePack,
                           @NotNull Hash hash,
                           @NotNull VirtualFile root) {
    int commitIndex = myLogData.getCommitIndex(hash, root);
    VisibleGraph<Integer> visibleGraph = visiblePack.getVisibleGraph();
    if (visibleGraph instanceof VisibleGraphImpl) {
      int nodeId = ((VisibleGraphImpl<Integer>)visibleGraph).getPermanentGraph().getPermanentCommitsInfo().getNodeId(commitIndex);
      if (nodeId == COMMIT_NOT_FOUND) return COMMIT_NOT_FOUND;
      if (nodeId < 0) return COMMIT_DOES_NOT_MATCH;
      Integer rowIndex = ((VisibleGraphImpl<Integer>)visibleGraph).getLinearGraph().getNodeIndex(nodeId);
      return rowIndex == null ? COMMIT_DOES_NOT_MATCH : rowIndex;
    }
    Integer rowIndex = visibleGraph.getVisibleRowIndex(commitIndex);
    return rowIndex == null ? COMMIT_DOES_NOT_MATCH : rowIndex;
  }

  @NotNull
  @Override
  public Map<VirtualFile, VcsLogProvider> getLogProviders() {
    return myLogData.getLogProviders();
  }

  @NotNull
  private VcsLogGraphTable getTable() {
    return myUi.getTable();
  }
}
