// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsProviderMarker;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FunctionUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.newHashSet;

/**
 * Interface for performing VCS checkin / commit / submit operations.
 *
 * @author lesya
 * @see com.intellij.openapi.vcs.AbstractVcs#getCheckinEnvironment()
 */
public interface CheckinEnvironment extends VcsProviderMarker {
  @Nullable
  RefreshableOnComponent createAdditionalOptionsPanel(@NotNull CheckinProjectPanel panel,
                                                      @NotNull PairConsumer<Object, Object> additionalDataConsumer);

  @Nullable
  default String getDefaultMessageFor(@NotNull FilePath[] filesToCheckin) {
    return null;
  }

  @Nullable
  @NonNls
  String getHelpId();

  String getCheckinOperationName();

  @Nullable
  default List<VcsException> commit(@NotNull List<Change> changes, @NotNull String preparedComment) {
    return commit(changes, preparedComment, FunctionUtil.nullConstant(), newHashSet());
  }

  @Nullable
  List<VcsException> commit(@NotNull List<Change> changes,
                            @NotNull String preparedComment,
                            @NotNull NullableFunction<Object, Object> parametersHolder,
                            Set<String> feedback);

  @Nullable
  List<VcsException> scheduleMissingFileForDeletion(@NotNull List<FilePath> files);

  @Nullable
  List<VcsException> scheduleUnversionedFilesForAddition(@NotNull List<VirtualFile> files);

  /**
   * @deprecated use {@link com.intellij.openapi.vcs.VcsConfiguration#REMOVE_EMPTY_INACTIVE_CHANGELISTS}
   */
  @SuppressWarnings("unused")
  @Deprecated
  default boolean keepChangeListAfterCommit(ChangeList changeList) {return false;}

  /**
   * @return true if VFS refresh has to be performed after commit, because files might have changed during commit
   * (for example, due to keyword substitution in SVN or read-only status in Perforce).
   */
  boolean isRefreshAfterCommitNeeded();
}
