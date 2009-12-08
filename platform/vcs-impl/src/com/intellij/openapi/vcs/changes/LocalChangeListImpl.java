/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class LocalChangeListImpl extends LocalChangeList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeList");

  private final Project myProject;
  private Collection<Change> myChanges = new HashSet<Change>();
  private Collection<Change> myReadChangesCache = null;
  private String myId;
  @NotNull private String myName;
  private String myComment = "";

  private boolean myIsDefault = false;
  private boolean myIsReadOnly = false;
  private ChangeHashSet myChangesBeforeUpdate;

  public static LocalChangeListImpl createEmptyChangeListImpl(Project project, String name) {
    return new LocalChangeListImpl(project, name);
  }

  private LocalChangeListImpl(Project project, final String name) {
    myProject = project;
    myName = name;
  }

  private LocalChangeListImpl(LocalChangeListImpl origin) {
    myId = origin.getId();
    myName = origin.myName;
    myProject = origin.myProject;
  }

  public synchronized Collection<Change> getChanges() {
    createReadChangesCache();
    return myReadChangesCache;
  }

  private void createReadChangesCache() {
    if (myReadChangesCache == null) {
      myReadChangesCache = Collections.unmodifiableCollection(new HashSet<Change>(myChanges));
    }
  }

  @NotNull
  @Override
  public synchronized String getId() {
    if (myId == null) {
      myId = UUID.randomUUID().toString();
    }

    return myId;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public void setName(@NotNull final String name) {
    if (! myName.equals(name)) {
      myName = name;
    }
  }

  public String getComment() {
    return myComment;
  }

  // same as for setName()
  public void setComment(final String comment) {
    if (! Comparing.equal(comment, myComment)) {
      myComment = comment != null ? comment : "";
    }
  }

  void setNameImpl(@NotNull final String name) {
    myName = name;
  }

  void setCommentImpl(final String comment) {
    myComment = comment;
  }

  public boolean isDefault() {
    return myIsDefault;
  }

  void setDefault(final boolean isDefault) {
    myIsDefault = isDefault;
  }

  public boolean isReadOnly() {
    return myIsReadOnly;
  }

  public void setReadOnly(final boolean isReadOnly) {
    myIsReadOnly = isReadOnly;
  }

  synchronized void addChange(Change change) {
    myReadChangesCache = null;
    myChanges.add(change);
  }

  synchronized Change removeChange(Change change) {
    for (Change localChange : myChanges) {
      if (localChange.equals(change)) {
        myChanges.remove(localChange);
        myReadChangesCache = null;
        return localChange;
      }
    }
    return null;
  }

  synchronized Collection<Change> startProcessingChanges(final Project project, @Nullable final VcsDirtyScope scope) {
    createReadChangesCache();
    final Collection<Change> result = new ArrayList<Change>();
    myChangesBeforeUpdate = new ChangeHashSet(myChanges);
    final ExcludedFileIndex fileIndex = ExcludedFileIndex.getInstance(project);
    for (Change oldBoy : myChangesBeforeUpdate) {
      final ContentRevision before = oldBoy.getBeforeRevision();
      final ContentRevision after = oldBoy.getAfterRevision();
      if (scope == null || before != null && scope.belongsTo(before.getFile()) || after != null && scope.belongsTo(after.getFile())
        || isIgnoredChange(oldBoy, fileIndex)) {
        result.add(oldBoy);
        removeChange(oldBoy);
      }
    }
    return result;
  }

  private static boolean isIgnoredChange(final Change change, final ExcludedFileIndex fileIndex) {
    boolean beforeRevIgnored = change.getBeforeRevision() == null || isIgnoredRevision(change.getBeforeRevision(), fileIndex);
    boolean afterRevIgnored = change.getAfterRevision() == null || isIgnoredRevision(change.getAfterRevision(), fileIndex);
    return beforeRevIgnored && afterRevIgnored;
  }

  private static boolean isIgnoredRevision(final ContentRevision revision, final ExcludedFileIndex fileIndex) {
    VirtualFile vFile = revision.getFile().getVirtualFile();
    return vFile != null && fileIndex.isExcludedFile(vFile);
  }

  synchronized boolean processChange(Change change) {
    LOG.debug("[process change] for '" + myName + "' isDefault: " + myIsDefault + " change: " +
              ChangesUtil.getFilePath(change).getPath());
    if (myIsDefault) {
      LOG.debug("[process change] adding because default");
      addChange(change);
      return true;
    }

    for (Change oldChange : myChangesBeforeUpdate) {
      if (Comparing.equal(oldChange, change)) {
        LOG.debug("[process change] adding bacuae equal to old: " + ChangesUtil.getFilePath(oldChange).getPath());
        addChange(change);
        return true;
      }
    }
    LOG.debug("[process change] not found");
    return false;
  }

  synchronized boolean doneProcessingChanges(final List<Change> removedChanges, final List<Change> addedChanges) {
    boolean changesDetected = (myChanges.size() != myChangesBeforeUpdate.size());

    for (Change newChange : myChanges) {
      Change oldChange = findOldChange(newChange);
      if (oldChange == null) {
        addedChanges.add(newChange);
      }
    }
    changesDetected |= (! addedChanges.isEmpty());
    final List<Change> removed = new ArrayList<Change>(myChangesBeforeUpdate);
    // since there are SAME objects...
    removed.removeAll(myChanges);
    removedChanges.addAll(removed);
    changesDetected = changesDetected || (! removedChanges.isEmpty());

    myReadChangesCache = null;
    return changesDetected;
  }

  @Nullable
  private Change findOldChange(final Change newChange) {
    Change oldChange = myChangesBeforeUpdate.getEqualChange(newChange);
    if (oldChange != null && sameBeforeRevision(oldChange, newChange) &&
        newChange.getFileStatus().equals(oldChange.getFileStatus())) {
      return oldChange;
    }
    return null;
  }

  private static boolean sameBeforeRevision(final Change change1, final Change change2) {
    final ContentRevision b1 = change1.getBeforeRevision();
    final ContentRevision b2 = change2.getBeforeRevision();
    if (b1 != null && b2 != null) {
      final VcsRevisionNumber rn1 = b1.getRevisionNumber();
      final VcsRevisionNumber rn2 = b2.getRevisionNumber();
      final boolean isBinary1 = (b1 instanceof BinaryContentRevision);
      final boolean isBinary2 = (b2 instanceof BinaryContentRevision);
      return rn1 != VcsRevisionNumber.NULL && rn2 != VcsRevisionNumber.NULL && rn1.compareTo(rn2) == 0 && isBinary1 == isBinary2;
    }
    return false;
  }

  public synchronized boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final LocalChangeListImpl list = (LocalChangeListImpl)o;

    if (myIsDefault != list.myIsDefault) return false;
    if (!myName.equals(list.myName)) return false;
    if (myIsReadOnly != list.myIsReadOnly) return false;

    return true;
  }

  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public String toString() {
    return myName.trim();
  }

  public synchronized LocalChangeList copy() {
    final LocalChangeListImpl copy = new LocalChangeListImpl(this);
    copy.myComment = myComment;
    copy.myIsDefault = myIsDefault;
    copy.myIsReadOnly = myIsReadOnly;

    if (myChanges != null) {
      copy.myChanges = new HashSet<Change>(myChanges);
    }

    if (myChangesBeforeUpdate != null) {
      copy.myChangesBeforeUpdate = new ChangeHashSet(myChangesBeforeUpdate);
    }

    if (myReadChangesCache != null) {
      copy.myReadChangesCache = new HashSet<Change>(myReadChangesCache);
    }

    return copy;
  }

  @Nullable
  public ChangeListEditHandler getEditHandler() {
    return null;
  }

  public void setId(String id) {
    myId = id;
  }

  private static class ChangeHashSet extends THashSet<Change> {
    public ChangeHashSet(final Collection<? extends Change> changes) {
      super(changes);
    }

    @Nullable
    Change getEqualChange(Change other) {
      int aIndex = index(other);
      if (aIndex >= 0) return (Change)_set [aIndex];
      return null;
    }
  }
}
