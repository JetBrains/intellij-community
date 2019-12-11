// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

public abstract class ChangeListManager implements ChangeListModification {
  @NotNull
  public static ChangeListManager getInstance(@NotNull Project project) {
    return project.getComponent(ChangeListManager.class);
  }

  public abstract void scheduleUpdate();

  /**
   * @deprecated use {@link #scheduleUpdate()}
   */
  @Deprecated
  public abstract void scheduleUpdate(boolean updateUnversionedFiles);


  public abstract void invokeAfterUpdate(@NotNull Runnable afterUpdate,
                                         @NotNull InvokeAfterUpdateMode mode,
                                         @Nullable String title,
                                         @Nullable ModalityState state);

  /**
   * @deprecated use {@link #invokeAfterUpdate(Runnable, InvokeAfterUpdateMode, String, ModalityState)}
   */
  @Deprecated
  public abstract void invokeAfterUpdate(@NotNull Runnable afterUpdate,
                                         @NotNull InvokeAfterUpdateMode mode,
                                         @Nullable String title,
                                         @Nullable Consumer<? super VcsDirtyScopeManager> dirtyScopeManager,
                                         @Nullable ModalityState state);


  public abstract int getChangeListsNumber();

  @NotNull
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
  public abstract String getDefaultListName();


  @NotNull
  public abstract List<File> getAffectedPaths();

  @NotNull
  public abstract List<VirtualFile> getAffectedFiles();

  /**
   * @return if a file belongs to some changelist
   */
  public abstract boolean isFileAffected(@NotNull VirtualFile file);


  @Nullable
  public abstract LocalChangeList findChangeList(String name);

  @Nullable
  public abstract LocalChangeList getChangeList(@Nullable String id);


  @NotNull
  public abstract List<LocalChangeList> getChangeLists(@NotNull Change change);

  @NotNull
  public abstract List<LocalChangeList> getChangeLists(@NotNull VirtualFile file);

  @Nullable
  public abstract LocalChangeList getChangeList(@NotNull Change change);

  @Nullable
  public abstract LocalChangeList getChangeList(@NotNull VirtualFile file);

  @Nullable
  public abstract String getChangeListNameIfOnlyOne(Change[] changes);


  @Nullable
  public abstract Change getChange(@NotNull VirtualFile file);

  @Nullable
  public abstract Change getChange(FilePath file);


  @NotNull
  public abstract FileStatus getStatus(@NotNull VirtualFile file);

  public abstract boolean isUnversioned(VirtualFile file);


  @NotNull
  public abstract Collection<Change> getChangesIn(@NotNull VirtualFile dir);

  @NotNull
  public abstract Collection<Change> getChangesIn(@NotNull FilePath path);

  @NotNull
  public abstract ThreeState haveChangesUnder(@NotNull VirtualFile vf);

  @Nullable
  public abstract AbstractVcs getVcsFor(@NotNull Change change);


  public abstract void addChangeListListener(@NotNull ChangeListListener listener, @NotNull Disposable disposable);

  public abstract void addChangeListListener(@NotNull ChangeListListener listener);

  public abstract void removeChangeListListener(@NotNull ChangeListListener listener);


  public abstract void registerCommitExecutor(@NotNull CommitExecutor executor);

  @NotNull
  public abstract List<CommitExecutor> getRegisteredExecutors();

  public abstract void commitChanges(@NotNull LocalChangeList changeList, @NotNull List<? extends Change> changes);


  public abstract void scheduleAutomaticEmptyChangeListDeletion(@NotNull LocalChangeList list);

  public abstract void scheduleAutomaticEmptyChangeListDeletion(@NotNull LocalChangeList list, boolean silently);

  /**
   * @deprecated All potential ignores should be contributed to VCS native ignores by corresponding {@link IgnoredFileProvider}.
   */
  @Deprecated
  @NotNull
  public abstract IgnoredFileBean[] getFilesToIgnore();

  public abstract boolean isIgnoredFile(@NotNull VirtualFile file);

  public abstract boolean isIgnoredFile(@NotNull FilePath file);

  /**
   * @deprecated All potential ignores should be contributed to VCS native ignores by corresponding {@link IgnoredFileProvider}.
   */
  @Deprecated
  public abstract void setFilesToIgnore(@NotNull IgnoredFileBean... ignoredFiles);

  /**
   * @deprecated All potential ignores should be contributed to VCS native ignores by corresponding {@link IgnoredFileProvider}.
   */
  @Deprecated
  public abstract void addFilesToIgnore(@NotNull IgnoredFileBean... ignoredFiles);

  /**
   * @deprecated All potential ignores should be contributed to VCS native ignores by corresponding {@link IgnoredFileProvider}.
   */
  @Deprecated
  public abstract void addDirectoryToIgnoreImplicitly(@NotNull String path);

  /**
   * @deprecated All potential ignores should be contributed to VCS native ignores by corresponding {@link IgnoredFileProvider}.
   */
  @Deprecated
  public abstract void removeImplicitlyIgnoredDirectory(@NotNull String path);


  @NotNull
  public abstract List<VirtualFile> getModifiedWithoutEditing();

  @Nullable
  public abstract String getSwitchedBranch(@NotNull VirtualFile file);


  @Nullable
  public abstract String isFreezed();

  public abstract boolean isFreezedWithNotification(@Nullable String modalTitle);


  @Deprecated // used in TeamCity
  public abstract void reopenFiles(@NotNull List<? extends FilePath> paths);

}
