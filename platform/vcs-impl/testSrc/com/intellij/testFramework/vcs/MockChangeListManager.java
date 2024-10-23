// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.vcs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.io.File;
import java.util.*;

public class MockChangeListManager extends ChangeListManagerEx {

  private final Map<String, MockChangeList> myChangeLists = new HashMap<>();
  private LocalChangeList myActiveChangeList;
  private final MockChangeList myDefaultChangeList;

  public MockChangeListManager() {
    myDefaultChangeList = new MockChangeList(LocalChangeList.getDefaultName());
    myChangeLists.put(LocalChangeList.getDefaultName(), myDefaultChangeList);
    myActiveChangeList = myDefaultChangeList;
  }

  public void addChanges(Change... changes) {
    MockChangeList changeList = myChangeLists.get(LocalChangeList.getDefaultName());
    for (Change change : changes) {
      changeList.add(change);
    }
  }

  @Override
  public void scheduleUpdate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void invokeAfterUpdate(@NotNull Runnable afterUpdate,
                                @NotNull InvokeAfterUpdateMode mode,
                                String title,
                                ModalityState state) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean areChangeListsEnabled() {
    return true;
  }

  @Override
  public int getChangeListsNumber() {
    return getChangeLists().size();
  }

  @NotNull
  @Override
  public List<LocalChangeList> getChangeLists() {
    return new ArrayList<>(myChangeLists.values());
  }

  @NotNull
  @Override
  public List<File> getAffectedPaths() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<VirtualFile> getAffectedFiles() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isFileAffected(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Collection<Change> getAllChanges() {
    Collection<Change> changes = new ArrayList<>();
    for (MockChangeList list : myChangeLists.values()) {
      changes.addAll(list.getChanges());
    }
    return changes;
  }

  @Override
  public LocalChangeList findChangeList(String name) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public LocalChangeList getChangeList(@Nullable String id) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public LocalChangeList getDefaultChangeList() {
    return myActiveChangeList;
  }

  @Override
  public LocalChangeList getChangeList(@NotNull Change change) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<LocalChangeList> getChangeLists(@NotNull Change change) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<LocalChangeList> getChangeLists(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getChangeListNameIfOnlyOne(Change[] changes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void scheduleAutomaticEmptyChangeListDeletion(@NotNull LocalChangeList list) {
    scheduleAutomaticEmptyChangeListDeletion(list, false);
  }

  @Override
  public void scheduleAutomaticEmptyChangeListDeletion(@NotNull LocalChangeList list, boolean silently) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Change getChange(@NotNull VirtualFile file) {
    return getChange(VcsUtil.getFilePath(file));
  }

  @Override
  public LocalChangeList getChangeList(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Change getChange(FilePath file) {
    for (Change change : getAllChanges()) {
      ContentRevision before = change.getBeforeRevision();
      ContentRevision after = change.getAfterRevision();
      if (after != null && after.getFile().equals(file) || before != null && before.getFile().equals(file)) {
        return change;
      }
    }
    return null;
  }

  @Override
  public boolean isUnversioned(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull List<FilePath> getUnversionedFilesPaths() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull FileStatus getStatus(@NotNull FilePath file) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public FileStatus getStatus(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Collection<Change> getChangesIn(@NotNull VirtualFile dir) {
    return getChangesIn(VcsUtil.getFilePath(dir));
  }

  @NotNull
  @Override
  public Collection<Change> getChangesIn(@NotNull FilePath path) {
    List<Change> changes = new ArrayList<>();
    for (Change change : getAllChanges()) {
      ContentRevision before = change.getBeforeRevision();
      ContentRevision after = change.getAfterRevision();
      if (before != null && before.getFile().isUnder(path, false) || after != null && after.getFile().isUnder(path, false)) {
        changes.add(change);
      }
    }
    return changes;
  }

  @NotNull
  @Override
  public ThreeState haveChangesUnder(@NotNull VirtualFile vf) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addChangeListListener(@NotNull ChangeListListener listener, @NotNull Disposable disposable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addChangeListListener(@NotNull ChangeListListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeChangeListListener(@NotNull ChangeListListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void registerCommitExecutor(@NotNull CommitExecutor executor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void commitChanges(@NotNull LocalChangeList changeList, @NotNull List<? extends Change> changes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reopenFiles(@NotNull List<? extends FilePath> paths) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<CommitExecutor> getRegisteredExecutors() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addUnversionedFiles(@NotNull LocalChangeList list, @NotNull List<? extends VirtualFile> unversionedFiles) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addDirectoryToIgnoreImplicitly(@NotNull String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setFilesToIgnore(IgnoredFileBean @NotNull ... ignoredFiles) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IgnoredFileBean @NotNull [] getFilesToIgnore() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isIgnoredFile(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isIgnoredFile(@NotNull FilePath file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull List<FilePath> getIgnoredFilePaths() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getSwitchedBranch(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String getDefaultListName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void freeze(@NotNull String reason) {
  }

  @Override
  public void unfreeze() {
  }

  @Override
  public void waitForUpdate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull Promise<?> promiseWaitForUpdate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String isFreezed() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isFreezedWithNotification(@Nullable String modalTitle) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<VirtualFile> getModifiedWithoutEditing() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public @NotNull LocalChangeList addChangeList(@NotNull String name, @Nullable String comment) {
    MockChangeList changeList = new MockChangeList(name);
    myChangeLists.put(name, changeList);
    return changeList;
  }

  @Override
  public void setDefaultChangeList(@NotNull String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setDefaultChangeList(@NotNull LocalChangeList list) {
    myActiveChangeList = list;
  }

  @Override
  public void setDefaultChangeList(@NotNull LocalChangeList list, boolean automatic) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeChangeList(@NotNull String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeChangeList(@NotNull LocalChangeList list) {
    myChangeLists.remove(list.getName());
    if (myActiveChangeList.equals(list)) {
      myActiveChangeList = myDefaultChangeList;
    }
  }

  @Override
  public void moveChangesTo(@NotNull LocalChangeList list, Change @NotNull ... changes) {
  }

  @Override
  public void moveChangesTo(@NotNull LocalChangeList list, @NotNull List<? extends @NotNull Change> changes) {
  }

  @Override
  public boolean setReadOnly(@NotNull String name, boolean value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean editName(@NotNull String fromName, @NotNull String toName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String editComment(@NotNull String fromName, String newComment) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean editChangeListData(@NotNull String name, @Nullable ChangeListData newData) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isInUpdate() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Collection<LocalChangeList> getAffectedLists(@NotNull Collection<? extends Change> changes) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public LocalChangeList addChangeList(@NotNull String name, @Nullable String comment, @Nullable ChangeListData data) {
    return addChangeList(name, comment);
  }

  @Override
  public void blockModalNotifications() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void unblockModalNotifications() {
    throw new UnsupportedOperationException();
  }
}
