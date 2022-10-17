// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.AbstractVcsLogUi;
import com.intellij.vcs.log.ui.table.VcsLogGraphTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;


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
    return myUi.getTable().getSelection().getCommits();
  }

  @NotNull
  @Override
  public List<VcsCommitMetadata> getSelectedShortDetails() {
    return myUi.getTable().getSelection().getCachedMetadata();
  }

  @NotNull
  @Override
  public List<VcsFullCommitDetails> getSelectedDetails() {
    return myUi.getTable().getSelection().getCachedFullDetails();
  }

  @Override
  public void requestSelectedDetails(@NotNull Consumer<? super List<? extends VcsFullCommitDetails>> consumer) {
    getTable().getSelection().requestFullDetails(consumer);
  }

  @Nullable
  @Override
  public Collection<String> getContainingBranches(@NotNull Hash commitHash, @NotNull VirtualFile root) {
    return myLogData.getContainingBranchesGetter().getContainingBranchesQuickly(root, commitHash);
  }

  @NotNull
  @Override
  public ListenableFuture<Boolean> jumpToReference(@NotNull String reference, boolean focus) {
    return VcsLogNavigationUtil.jumpToRefOrHash(myUi, reference, false, focus);
  }

  @Override
  @NotNull
  public ListenableFuture<Boolean> jumpToCommit(@NotNull Hash commitHash, @NotNull VirtualFile root, boolean focus) {
    return VcsLogNavigationUtil.jumpToCommit(myUi, commitHash, root, false, focus);
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
