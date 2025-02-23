// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.*;

import java.io.File;
import java.util.Collection;
import java.util.List;

public abstract class ChangeListManager implements ChangeListModification {
  public static @NotNull ChangeListManager getInstance(@NotNull Project project) {
    if (project.isDefault()) throw new IllegalArgumentException("Can't create ChangeListManager for default project");
    return project.getService(ChangeListManager.class);
  }

  /**
   * Invoke callback when current CLM refresh is completed, without any visible progress.
   * <p/>
   * WARNING: This callback WILL NOT wait for async unchanged files update if VCS is using a custom {@link VcsManagedFilesHolder}.
   * These can be listened via {@link ChangeListListener#unchangedFileStatusChanged(boolean)} or on a per-VCS basis.
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


  /**
   * Whether changelists are enabled.
   * <p/>
   * Ex: Changelists can be disabled if the only VCS in the project is 'Git' in a "Staging Area" commit mode.
   * <p/>
   * When disabled:
   * <ul>
   * <li/> All modification requests on changelists will log an error.
   * <li/> All read requests will return a single 'blank' default changelist.
   * </ul>
   */
  public abstract boolean areChangeListsEnabled();

  public abstract int getChangeListsNumber();

  /**
   * @deprecated Use {@link #getChangeLists()} instead.
   */
  @Deprecated
  public @NotNull List<LocalChangeList> getChangeListsCopy() {
    return getChangeLists();
  }

  public abstract @NotNull @Unmodifiable List<LocalChangeList> getChangeLists();

  public abstract @NotNull Collection<Change> getAllChanges();

  /**
   * Currently active change list.
   * All new {@link Change} will be moved into this changelist by default.
   *
   * @see #setDefaultChangeList(String)
   * @see #setDefaultChangeList(LocalChangeList)
   * @see com.intellij.openapi.vcs.impl.PartialChangesUtil#computeUnderChangeListSync
   */
  public abstract @NotNull LocalChangeList getDefaultChangeList();

  public abstract @NotNull @NlsSafe String getDefaultListName();


  /**
   * @return all files that belong to some changelist (have an associated {@link Change}).
   */
  public abstract @NotNull List<File> getAffectedPaths();

  public abstract @NotNull List<VirtualFile> getAffectedFiles();

  /**
   * @return if a file belongs to some changelist
   */
  public abstract boolean isFileAffected(@NotNull VirtualFile file);

  /**
   * @see LocalChangeList#getName()
   */
  public abstract @Nullable LocalChangeList findChangeList(@NlsSafe String name);

  /**
   * @see LocalChangeList#getId()
   */
  public abstract @Nullable LocalChangeList getChangeList(@Nullable @NonNls String id);


  public abstract @NotNull List<LocalChangeList> getChangeLists(@NotNull Change change);

  public abstract @NotNull List<LocalChangeList> getChangeLists(@NotNull VirtualFile file);

  public abstract @Nullable LocalChangeList getChangeList(@NotNull Change change);

  public abstract @Nullable LocalChangeList getChangeList(@NotNull VirtualFile file);

  public abstract @Nullable @NlsSafe String getChangeListNameIfOnlyOne(Change[] changes);


  public abstract @Nullable Change getChange(@NotNull VirtualFile file);

  public abstract @Nullable Change getChange(FilePath file);


  public abstract @NotNull FileStatus getStatus(@NotNull FilePath file);

  public abstract @NotNull FileStatus getStatus(@NotNull VirtualFile file);

  public abstract boolean isUnversioned(@NotNull VirtualFile file);

  public abstract @NotNull List<FilePath> getUnversionedFilesPaths();

  /**
   * @return All the changes under a given path (inc. from other VCS roots)
   * @see com.intellij.vcsUtil.VcsImplUtil#filterChangesUnderFiles
   */
  public abstract @NotNull Collection<Change> getChangesIn(@NotNull VirtualFile dir);

  /**
   * @return All the changes under a given path (inc. from other VCS roots)
   * @see com.intellij.vcsUtil.VcsImplUtil#filterChangesUnder
   */
  public abstract @NotNull Collection<Change> getChangesIn(@NotNull FilePath path);

  /**
   * Check if a directory has modified children in {@link #getAllChanges().
   *
   * @return {@code ThreeState.YES} if directory has an immediate modified child,
   * {@code ThreeState.UNSURE} if directory has non-immediate modified child (depth > 1),
   * {@code ThreeState.NO} if directory has no modified children.
   */
  public abstract @NotNull ThreeState haveChangesUnder(@NotNull VirtualFile vf);


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
   * @deprecated use {@link CommitExecutor#LOCAL_COMMIT_EXECUTOR} extension point
   */
  @Deprecated(forRemoval = true)
  public abstract void registerCommitExecutor(@NotNull CommitExecutor executor);

  public abstract @NotNull List<CommitExecutor> getRegisteredExecutors();

  public abstract void commitChanges(@NotNull LocalChangeList changeList, @NotNull List<? extends Change> changes);


  public abstract void scheduleAutomaticEmptyChangeListDeletion(@NotNull LocalChangeList list);

  /**
   * Remove an empty changelist that is not needed anymore.
   * Ex: can be called after committing or shelving all changes in a changelist.
   *
   * @param silently whether to prompt user about removal, see {@link  VcsConfiguration#REMOVE_EMPTY_INACTIVE_CHANGELISTS}.
   */
  public abstract void scheduleAutomaticEmptyChangeListDeletion(@NotNull LocalChangeList list, boolean silently);

  /**
   * @return an empty array.
   * @deprecated All potential ignores should be contributed to VCS native ignores by corresponding {@link IgnoredFileProvider}.
   */
  @Deprecated(forRemoval = true)
  public abstract IgnoredFileBean @NotNull [] getFilesToIgnore();

  public abstract boolean isIgnoredFile(@NotNull VirtualFile file);

  public abstract boolean isIgnoredFile(@NotNull FilePath file);

  public abstract @NotNull List<FilePath> getIgnoredFilePaths();

  /**
   * @deprecated All potential ignores should be contributed to VCS native ignores by corresponding {@link IgnoredFileProvider}.
   */
  @Deprecated(forRemoval = true)
  public abstract void setFilesToIgnore(IgnoredFileBean @NotNull ... ignoredFiles);

  /**
   * @deprecated All potential ignores should be contributed to VCS native ignores by corresponding {@link IgnoredFileProvider}.
   */
  @Deprecated(forRemoval = true)
  public abstract void addDirectoryToIgnoreImplicitly(@NotNull @NlsSafe String path);

  /**
   * Files that were modified without an explicit checkout (ex: in Perforce).
   *
   * @see FileStatus#HIJACKED
   */
  public abstract @NotNull List<VirtualFile> getModifiedWithoutEditing();

  /**
   * Files that were checked-out from another branch, different from the rest of the repository (ex: in Subversion).
   *
   * @see FileStatus#SWITCHED
   */
  public abstract @Nullable @NlsSafe String getSwitchedBranch(@NotNull VirtualFile file);

  /**
   * Whether {@link ChangeListManager} updating is temporally disabled to preserve changes-to-changelist mapping during a complex operation.
   * Ex: during 'shelve-change branch-unshelve' routine.
   *
   * @see com.intellij.openapi.vcs.changes.ChangeListManagerEx#freeze(String)
   */
  public abstract @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String isFreezed();

  /**
   * Show an error message if the manager is frozen and action cannot be performed.
   *
   * @see #isFreezed()
   */
  public abstract boolean isFreezedWithNotification(@NlsContexts.DialogTitle @Nullable String modalTitle);

  @Deprecated(forRemoval = true)
  public abstract void reopenFiles(@NotNull List<? extends FilePath> paths);
}
