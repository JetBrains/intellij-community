package com.intellij.testFramework.vcs;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.continuation.ContinuationPause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class MockChangeListManager extends ChangeListManager {

  public static final String DEFAULT_CHANGE_LIST_NAME = "Default";

  private final Map<String, MockChangeList> myChangeLists = new HashMap<String, MockChangeList>();
  private LocalChangeList myActiveChangeList;

  public MockChangeListManager() {
    MockChangeList defaultChangeList = new MockChangeList(DEFAULT_CHANGE_LIST_NAME);
    myChangeLists.put(DEFAULT_CHANGE_LIST_NAME, defaultChangeList);
    myActiveChangeList = defaultChangeList;
  }

  public void addChanges(Change... changes) {
    MockChangeList changeList = myChangeLists.get(DEFAULT_CHANGE_LIST_NAME);
    for (Change change : changes) {
      changeList.add(change);
    }
  }

  @Override
  public void scheduleUpdate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void scheduleUpdate(boolean updateUnversionedFiles) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void invokeAfterUpdate(Runnable afterUpdate,
                                InvokeAfterUpdateMode mode,
                                String title,
                                ModalityState state) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void invokeAfterUpdate(Runnable afterUpdate,
                                InvokeAfterUpdateMode mode,
                                String title,
                                Consumer<VcsDirtyScopeManager> dirtyScopeManager,
                                ModalityState state) {
    afterUpdate.run();
  }

  @Override
  public boolean ensureUpToDate(boolean canBeCanceled) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<LocalChangeList> getChangeListsCopy() {
    return new ArrayList<LocalChangeList>(myChangeLists.values());
  }

  @NotNull
  @Override
  public List<LocalChangeList> getChangeLists() {
    return getChangeListsCopy();
  }

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
  public boolean isFileAffected(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Collection<Change> getAllChanges() {
    Collection<Change> changes = new ArrayList<Change>();
    for (MockChangeList list : myChangeLists.values()) {
      changes.addAll(list.getChanges());
    }
    return changes;
  }

  @Override
  public LocalChangeList findChangeList(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public LocalChangeList getChangeList(String id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public LocalChangeList getDefaultChangeList() {
    return myActiveChangeList;
  }

  @Override
  public boolean isDefaultChangeList(ChangeList list) {
    throw new UnsupportedOperationException();
  }

  @Override
  public LocalChangeList getChangeList(Change change) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getChangeListNameIfOnlyOne(Change[] changes) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Runnable prepareForChangeDeletion(Collection<Change> changes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Change getChange(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public LocalChangeList getChangeList(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Change getChange(FilePath file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isUnversioned(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public FileStatus getStatus(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Collection<Change> getChangesIn(VirtualFile dir) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Collection<Change> getChangesIn(FilePath path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addChangeListListener(ChangeListListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeChangeListListener(ChangeListListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void registerCommitExecutor(CommitExecutor executor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void commitChanges(LocalChangeList changeList, List<Change> changes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void commitChangesSynchronously(LocalChangeList changeList, List<Change> changes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean commitChangesSynchronouslyWithResult(LocalChangeList changeList, List<Change> changes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reopenFiles(List<FilePath> paths) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<CommitExecutor> getRegisteredExecutors() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addFilesToIgnore(IgnoredFileBean... ignoredFiles) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setFilesToIgnore(IgnoredFileBean... ignoredFiles) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IgnoredFileBean[] getFilesToIgnore() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isIgnoredFile(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getSwitchedBranch(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getDefaultListName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void freeze(ContinuationPause context, String reason) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void letGo() {
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

  @Override
  public List<VirtualFile> getModifiedWithoutEditing() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public LocalChangeList addChangeList(@NotNull String name, @Nullable String comment) {
    MockChangeList changeList = new MockChangeList(name);
    myChangeLists.put(name, changeList);
    return changeList;
  }

  @Override
  public void setDefaultChangeList(@NotNull LocalChangeList list) {
    myActiveChangeList = list;
  }

  @Override
  public void removeChangeList(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeChangeList(LocalChangeList list) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void moveChangesTo(LocalChangeList list, Change[] changes) {
  }

  @Override
  public boolean setReadOnly(String name, boolean value) {
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
}
