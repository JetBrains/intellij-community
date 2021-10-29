// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
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
   * Returns commits currently selected in the log.
   */
  @NotNull
  List<CommitId> getSelectedCommits();

  /**
   * Returns cached metadata of the commits selected in the table.
   * For commits that are not loaded a placeholder object
   * (an instance of {@link com.intellij.vcs.log.data.LoadingDetails}) is returned.
   * <br/>
   * Metadata are loaded faster than full details and since it is done while scrolling,
   * there is a better chance that details for a commit are loaded when user selects it.
   * This makes this method preferable to {@link #getSelectedDetails()}.
   *
   * @see com.intellij.vcs.log.data.LoadingDetails
   */
  @NotNull
  List<VcsCommitMetadata> getSelectedShortDetails();

  /**
   * Returns cached details of the selected commits.
   * For commits that are not loaded a placeholder object
   * (an instance of {@link com.intellij.vcs.log.data.LoadingDetails}) is returned.
   *
   * @see com.intellij.vcs.log.data.LoadingDetails
   */
  @NotNull
  List<VcsFullCommitDetails> getSelectedDetails();

  /**
   * Sends a request to load details that are currently selected.
   * Details are loaded in background. If a progress indicator is specified it is used during loading process.
   * After all details are loaded they are provided to the consumer in the EDT.
   *
   * @param consumer called in EDT after all details are loaded.
   */
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
   * @param focus focus VCS Log table
   */
  @NotNull
  Future<Boolean> jumpToReference(@NotNull String reference, boolean focus);

  /**
   * {@link #jumpToReference(String, boolean)} with focusing VCS Log table
   */
  @NotNull
  default Future<Boolean> jumpToReference(@NotNull String reference){
    return jumpToReference(reference, true);
  }

  /**
   * Asynchronously selects the given commit in the given root.
   * Returns a {@link Future future} that allows to check if the commit was selected, wait for the selection while log is being loaded,
   * or cancel commit selection.
   *
   * @param commitHash target commit
   * @param root target repository root
   * @param focus focus VCS Log table
   */
  @NotNull
  Future<Boolean> jumpToCommit(@NotNull Hash commitHash, @NotNull VirtualFile root, boolean focus);

  /**
   * {@link #jumpToCommit(Hash, VirtualFile, boolean)} with focusing VCS Log table
   */
  @NotNull
  default Future<Boolean> jumpToCommit(@NotNull Hash commitHash, @NotNull VirtualFile root) {
    return jumpToCommit(commitHash, root, true);
  }

  /**
   * Returns {@link VcsLogProvider VcsLogProviders} which are active in this log, i.e. which VCS roots are shown in the log.
   */
  @NotNull
  Map<VirtualFile, VcsLogProvider> getLogProviders();
}
