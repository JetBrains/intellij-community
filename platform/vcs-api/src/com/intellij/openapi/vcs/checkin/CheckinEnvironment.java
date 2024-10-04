// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.util.NlsContexts.DetailedDescription;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Interface for performing VCS checkin / commit / submit operations.
 *
 * @see com.intellij.openapi.vcs.AbstractVcs#getCheckinEnvironment()
 */
public interface CheckinEnvironment {

  @Nullable
  default RefreshableOnComponent createCommitOptions(@NotNull CheckinProjectPanel commitPanel, @NotNull CommitContext commitContext) {
    //noinspection deprecation
    return createAdditionalOptionsPanel(commitPanel, commitContext.getAdditionalDataConsumer());
  }

  /**
   * @deprecated use {@link #createCommitOptions(CheckinProjectPanel, CommitContext)}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @Nullable
  default RefreshableOnComponent createAdditionalOptionsPanel(@NotNull CheckinProjectPanel panel,
                                                              @NotNull PairConsumer<Object, Object> additionalDataConsumer) {
    return null;
  }

  @Nullable
  default @NlsSafe String getDefaultMessageFor(FilePath @NotNull [] filesToCheckin) {
    return null;
  }

  @Nullable
  @NonNls
  String getHelpId();

  @Nls(capitalization = Nls.Capitalization.Title)
  String getCheckinOperationName();

  @Nullable
  default List<VcsException> commit(@NotNull List<? extends Change> changes, @NotNull @NlsSafe String preparedComment) {
    return commit(changes, preparedComment, new CommitContext(), new HashSet<>());
  }

  @Nullable
  default List<VcsException> commit(@NotNull List<? extends Change> changes,
                                    @NotNull @NlsSafe String commitMessage,
                                    @NotNull CommitContext commitContext,
                                    @NotNull Set<? super @DetailedDescription String> feedback) {
    //noinspection deprecation
    return commit(changes, commitMessage, commitContext.getAdditionalData(), feedback);
  }

  /**
   * @deprecated use {@link #commit(List, String, CommitContext, Set)}
   */
  @SuppressWarnings("unused")
  @Deprecated
  @Nullable
  default List<VcsException> commit(@NotNull List<? extends Change> changes,
                                    @NotNull String preparedComment,
                                    @NotNull NullableFunction<Object, Object> parametersHolder,
                                    @NotNull Set<? super @DetailedDescription String> feedback) {
    return null;
  }

  @Nullable
  List<VcsException> scheduleMissingFileForDeletion(@NotNull List<? extends FilePath> files);

  @Nullable
  List<VcsException> scheduleUnversionedFilesForAddition(@NotNull List<? extends VirtualFile> files);

  /**
   * @deprecated use {@link com.intellij.openapi.vcs.VcsConfiguration#REMOVE_EMPTY_INACTIVE_CHANGELISTS}
   */
  @SuppressWarnings("unused")
  @Deprecated
  default boolean keepChangeListAfterCommit(ChangeList changeList) { return false; }

  /**
   * @return true if VFS refresh has to be performed after commit, because files might have changed during commit
   * (for example, due to keyword substitution in SVN or read-only status in Perforce).
   */
  boolean isRefreshAfterCommitNeeded();

  @Nullable
  default PostCommitChangeConverter getPostCommitChangeConverter() {
    return null;
  }
}
