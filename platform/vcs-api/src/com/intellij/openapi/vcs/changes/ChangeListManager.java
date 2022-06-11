// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.*;

import java.io.File;
import java.util.Collection;
import java.util.List;

public abstract class ChangeListManager implements ChangeListModification {
  @NotNull
  public static ChangeListManager getInstance(@NotNull Project project) {
    return project.getService(ChangeListManager.class);
  }

  public abstract void scheduleUpdate();

  /**
   * Invoke callback when current CLM refresh is completed, without any visible progress.
   */
  public void invokeAfterUpdate(boolean callbackOnAwt, @NotNull Runnable afterUpdate) {
    InvokeAfterUpdateMode mode = callbackOnAwt ? InvokeAfterUpdateMode.SILENT : InvokeAfterUpdateMode.SILENT_CALLBACK_POOLED;
    invokeAfterUpdate(afterUpdate, mode, null, null);
  }

  /**
   * Invoke callback when current refresh is completed. Show background progress while waiting.
   *
   * @param cancellable Whether the progress can be cancelled. If progress is cancelled, callback will not be called.
   * @param title       Operation name to use as prefix for progress text
   * @param afterUpdate Callback that will be called in {@link com.intellij.openapi.progress.Task#onFinished()}
   */
  public void invokeAfterUpdateWithProgress(boolean cancellable,
                                            @Nullable @NlsContexts.ProgressTitle String title,
                                            @NotNull Runnable afterUpdate) {
    InvokeAfterUpdateMode mode = cancellable ? InvokeAfterUpdateMode.BACKGROUND_CANCELLABLE
                                             : InvokeAfterUpdateMode.BACKGROUND_NOT_CANCELLABLE;
    invokeAfterUpdate(afterUpdate, mode, title, null);
  }

  /**
   * Invoke callback when current refresh is completed with modal progress.
   *
   * @param cancellable Whether the progress can be cancelled. If progress is cancelled, callback will be called without waiting for the current CLM refresh to finish.
   * @param title       Operation name to use as prefix for progress dialog title
   * @param afterUpdate Callback that will be called in {@link com.intellij.openapi.progress.Task#onFinished()}
   */
  public void invokeAfterUpdateWithModal(boolean cancellable,
                                         @Nullable @NlsContexts.DialogTitle String title,
                                         @NotNull Runnable afterUpdate) {
    InvokeAfterUpdateMode mode = cancellable ? InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE
                                             : InvokeAfterUpdateMode.SYNCHRONOUS_NOT_CANCELLABLE;
    invokeAfterUpdate(afterUpdate, mode, title, null);
  }

  /**
   * @see #invokeAfterUpdate(boolean, Runnable)
   * @see #invokeAfterUpdateWithProgress
   * @see #invokeAfterUpdateWithModal
   */
  public abstract void invokeAfterUpdate(@NotNull Runnable afterUpdate,
                                         @NotNull InvokeAfterUpdateMode mode,
                                         @Nullable @Nls String title,
                                         @Nullable ModalityState state);


  public abstract boolean areChangeListsEnabled();

  public abstract int getChangeListsNumber();

  /**
   * @deprecated Use {@link #getChangeLists()} instead.
   */
  @NotNull
  @Deprecated
  public List<LocalChangeList> getChangeListsCopy() {
    return getChangeLists();
  }

  @NotNull
  public abstract List<LocalChangeList> getChangeLists();

  @NotNull
  public abstract Collection<Change> getAllChanges();

  /**
   *  Currently active change list.
   *  @see #setDefaultChangeList(String)
   *  @see #setDefaultChangeList(LocalChangeList)
   */
  @NotNull
  public abstract LocalChangeList getDefaultChangeList();

  @NotNull
  public abstract @NlsSafe String getDefaultListName();


  @NotNull
  public abstract List<File> getAffectedPaths();

  @NotNull
  public abstract List<VirtualFile> getAffectedFiles();

  /**
   * @return if a file belongs to some changelist
   */
  public abstract boolean isFileAffected(@NotNull VirtualFile file);


  @Nullable
  public abstract LocalChangeList findChangeList(@NlsSafe String name);

  @Nullable
  public abstract LocalChangeList getChangeList(@Nullable @NonNls String id);


  @NotNull
  public abstract List<LocalChangeList> getChangeLists(@NotNull Change change);

  @NotNull
  public abstract List<LocalChangeList> getChangeLists(@NotNull VirtualFile file);

  @Nullable
  public abstract LocalChangeList getChangeList(@NotNull Change change);

  @Nullable
  public abstract LocalChangeList getChangeList(@NotNull VirtualFile file);

  @Nullable
  public abstract @NlsSafe String getChangeListNameIfOnlyOne(Change[] changes);


  @Nullable
  public abstract Change getChange(@NotNull VirtualFile file);

  @Nullable
  public abstract Change getChange(FilePath file);


  @NotNull
  public abstract FileStatus getStatus(@NotNull FilePath file);

  @NotNull
  public abstract FileStatus getStatus(@NotNull VirtualFile file);

  public abstract boolean isUnversioned(VirtualFile file);

  @NotNull
  public abstract List<FilePath> getUnversionedFilesPaths();

  @NotNull
  public abstract Collection<Change> getChangesIn(@NotNull VirtualFile dir);

  @NotNull
  public abstract Collection<Change> getChangesIn(@NotNull FilePath path);

  @NotNull
  public abstract ThreeState haveChangesUnder(@NotNull VirtualFile vf);

  /**
   * @deprecated Use {@link com.intellij.openapi.vcs.ProjectLevelVcsManager#getVcsFor}
   */
  @Nullable
  @Deprecated(forRemoval = true)
  public abstract AbstractVcs getVcsFor(@NotNull Change change);


  /**
   * Prefer using {@link ChangeListListener#TOPIC}
   */
  public abstract void addChangeListListener(@NotNull ChangeListListener listener, @NotNull Disposable disposable);

  /**
   * Prefer using {@link ChangeListListener#TOPIC}
   */
  public abstract void addChangeListListener(@NotNull ChangeListListener listener);

  public abstract void removeChangeListListener(@NotNull ChangeListListener listener);


  /**
   * @deprecated use {@link LocalCommitExecutor#LOCAL_COMMIT_EXECUTOR} extension point
   */
  @Deprecated(forRemoval = true)
  public abstract void registerCommitExecutor(@NotNull CommitExecutor executor);

  @NotNull
  public abstract List<CommitExecutor> getRegisteredExecutors();

  public abstract void commitChanges(@NotNull LocalChangeList changeList, @NotNull List<? extends Change> changes);


  public abstract void scheduleAutomaticEmptyChangeListDeletion(@NotNull LocalChangeList list);

  public abstract void scheduleAutomaticEmptyChangeListDeletion(@NotNull LocalChangeList list, boolean silently);

  /**
   * @deprecated All potential ignores should be contributed to VCS native ignores by corresponding {@link IgnoredFileProvider}.
   */
  @Deprecated(forRemoval = true)
  public abstract IgnoredFileBean @NotNull [] getFilesToIgnore();

  public abstract boolean isIgnoredFile(@NotNull VirtualFile file);

  public abstract boolean isIgnoredFile(@NotNull FilePath file);

  @NotNull
  public abstract List<FilePath> getIgnoredFilePaths();

  /**
   * @deprecated All potential ignores should be contributed to VCS native ignores by corresponding {@link IgnoredFileProvider}.
   */
  @Deprecated(forRemoval = true)
  public abstract void setFilesToIgnore(IgnoredFileBean @NotNull ... ignoredFiles);

  /**
   * @deprecated All potential ignores should be contributed to VCS native ignores by corresponding {@link IgnoredFileProvider}.
   */
  @Deprecated(forRemoval = true)
  public abstract void addFilesToIgnore(IgnoredFileBean @NotNull ... ignoredFiles);

  /**
   * @deprecated All potential ignores should be contributed to VCS native ignores by corresponding {@link IgnoredFileProvider}.
   */
  @Deprecated(forRemoval = true)
  public abstract void addDirectoryToIgnoreImplicitly(@NotNull @NlsSafe String path);


  @NotNull
  public abstract List<VirtualFile> getModifiedWithoutEditing();

  @Nullable
  public abstract @NlsSafe String getSwitchedBranch(@NotNull VirtualFile file);


  @Nullable
  public abstract @Nls(capitalization = Nls.Capitalization.Sentence) String isFreezed();

  public abstract boolean isFreezedWithNotification(@NlsContexts.DialogTitle @Nullable String modalTitle);

  @Deprecated(forRemoval = true)
  public abstract void reopenFiles(@NotNull List<? extends FilePath> paths);

}
