// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.VcsLogUiEx;
import com.intellij.vcs.log.ui.table.VcsLogCommitList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;


public class VcsLogImpl implements VcsLog {
  private final @NotNull VcsLogData myLogData;
  private final @NotNull VcsLogUiEx myUi;

  public VcsLogImpl(@NotNull VcsLogData manager, @NotNull VcsLogUiEx ui) {
    myLogData = manager;
    myUi = ui;
  }

  @Override
  public @NotNull List<CommitId> getSelectedCommits() {
    return myUi.getTable().getSelection().getCommits();
  }

  @Override
  public @NotNull List<VcsCommitMetadata> getSelectedShortDetails() {
    return myUi.getTable().getSelection().getCachedMetadata();
  }

  @Override
  public @NotNull List<VcsFullCommitDetails> getSelectedDetails() {
    return myUi.getTable().getSelection().getCachedFullDetails();
  }

  @Override
  public void requestSelectedDetails(@NotNull Consumer<? super List<? extends VcsFullCommitDetails>> consumer) {
    getTable().getSelection().requestFullDetails(consumer::consume);
  }

  @Override
  public @Nullable Collection<String> getContainingBranches(@NotNull Hash commitHash, @NotNull VirtualFile root) {
    return myLogData.getContainingBranchesGetter().getContainingBranchesQuickly(root, commitHash);
  }

  @Override
  public @NotNull ListenableFuture<Boolean> jumpToReference(@NotNull String reference, boolean focus) {
    return VcsLogNavigationUtil.jumpToRefOrHash(myUi, reference, false, focus);
  }

  @Override
  public @NotNull ListenableFuture<Boolean> jumpToCommit(@NotNull Hash commitHash, @NotNull VirtualFile root, boolean focus) {
    return VcsLogNavigationUtil.jumpToCommit(myUi, commitHash, root, false, focus);
  }

  @Override
  public @NotNull Map<VirtualFile, VcsLogProvider> getLogProviders() {
    return myLogData.getLogProviders();
  }

  private @NotNull VcsLogCommitList getTable() {
    return myUi.getTable();
  }
}
