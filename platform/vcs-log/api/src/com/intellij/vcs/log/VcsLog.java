// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Use this interface to access information available in the VCS Log.
 */
public interface VcsLog {

  /**
   * @deprecated Use {@link VcsLogCommitSelection#getCommits()} instead.
   */
  @RequiresEdt
  @NotNull
  @Deprecated
  List<CommitId> getSelectedCommits();

  /**
   * @deprecated Use {@link VcsLogCommitSelection#getCachedMetadata()} instead.
   */
  @RequiresEdt
  @NotNull
  @Deprecated
  List<VcsCommitMetadata> getSelectedShortDetails();

  /**
   * @deprecated Use {@link VcsLogCommitSelection#getCachedFullDetails()} instead.
   */
  @RequiresEdt
  @NotNull
  @Deprecated
  List<VcsFullCommitDetails> getSelectedDetails();

  /**
   * @deprecated Use {@link VcsLogCommitSelection#requestFullDetails(java.util.function.Consumer)} instead.
   */
  @RequiresEdt
  @Deprecated
  void requestSelectedDetails(@NotNull Consumer<? super List<? extends VcsFullCommitDetails>> consumer);

  /**
   * Returns names of branches which contain the given commit, or null if this information is unavailable yet.
   */
  @Nullable
  Collection<String> getContainingBranches(@NotNull Hash commitHash, @NotNull VirtualFile root);

  /**
   * Asynchronously selects the commit node defined by the given reference (commit hash, branch or tag).
   * Returns a {@link Future future} that allows to check if the commit was selected, wait for the selection while log is being loaded,
   * or cancel commit selection.
   *
   * @param reference target reference (commit hash, branch or tag)
   * @param focus     focus VCS Log table
   */
  @NotNull
  Future<Boolean> jumpToReference(@NotNull String reference, boolean focus);

  /**
   * {@link #jumpToReference(String, boolean)} with focusing VCS Log table
   */
  default @NotNull Future<Boolean> jumpToReference(@NotNull String reference) {
    return jumpToReference(reference, true);
  }

  /**
   * Asynchronously selects the given commit in the given root.
   * Returns a {@link Future future} that allows to check if the commit was selected, wait for the selection while log is being loaded,
   * or cancel commit selection.
   *
   * @param commitHash target commit
   * @param root       target repository root
   * @param focus      focus VCS Log table
   */
  @NotNull
  Future<Boolean> jumpToCommit(@NotNull Hash commitHash, @NotNull VirtualFile root, boolean focus);

  /**
   * {@link #jumpToCommit(Hash, VirtualFile, boolean)} with focusing VCS Log table
   */
  default @NotNull Future<Boolean> jumpToCommit(@NotNull Hash commitHash, @NotNull VirtualFile root) {
    return jumpToCommit(commitHash, root, true);
  }

  /**
   * Returns {@link VcsLogProvider VcsLogProviders} which are active in this log, i.e. which VCS roots are shown in the log.
   */
  @NotNull
  Map<VirtualFile, VcsLogProvider> getLogProviders();
}
