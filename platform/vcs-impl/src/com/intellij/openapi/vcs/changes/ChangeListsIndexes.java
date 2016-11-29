/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes;

import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BeforeAfter;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.newHashSet;

public class ChangeListsIndexes {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeListsIndexes");
  private final TreeMap<FilePath, FileStatus> myFileToStatus;
  private final Map<FilePath, Pair<VcsKey, VcsRevisionNumber>> myFileToVcs;

  ChangeListsIndexes() {
    myFileToStatus = new TreeMap<>(HierarchicalFilePathComparator.SYSTEM_CASE_SENSITIVE);
    myFileToVcs = new HashMap<>();
  }

  ChangeListsIndexes(final ChangeListsIndexes idx) {
    myFileToStatus = new TreeMap<>(idx.myFileToStatus);
    myFileToVcs = new HashMap<>(idx.myFileToVcs);
  }

  void add(final FilePath file, final FileStatus status, final VcsKey key, VcsRevisionNumber number) {
    myFileToStatus.put(file, status);
    myFileToVcs.put(file, Pair.create(key, number));
    if (LOG.isDebugEnabled()) {
      LOG.debug("Set status " + status + " for " + file);
    }
  }

  void remove(final FilePath file) {
    myFileToStatus.remove(file);
    myFileToVcs.remove(file);
  }

  public FileStatus getStatus(final VirtualFile file) {
    return myFileToStatus.get(VcsUtil.getFilePath(file));
  }
  
  public FileStatus getStatus(@NotNull FilePath file) {
    return myFileToStatus.get(file);
  }

  public void changeAdded(final Change change, final VcsKey key) {
    addChangeToIdx(change, key);
  }

  public void changeRemoved(final Change change) {
    final ContentRevision afterRevision = change.getAfterRevision();
    final ContentRevision beforeRevision = change.getBeforeRevision();

    if (afterRevision != null) {
      remove(afterRevision.getFile());
    }
    if (beforeRevision != null) {
      remove(beforeRevision.getFile());
    }
  }

  @Nullable
  public VcsKey getVcsFor(@NotNull Change change) {
    VcsKey key = getVcsForRevision(change.getAfterRevision());
    if (key != null) return key;
    return getVcsForRevision(change.getBeforeRevision());
  }

  @Nullable
  private VcsKey getVcsForRevision(@Nullable ContentRevision revision) {
    if (revision != null) {
      Pair<VcsKey, VcsRevisionNumber> pair = myFileToVcs.get(revision.getFile());
      return pair == null ? null : pair.getFirst();
    }
    return null;
  }

  private void addChangeToIdx(final Change change, final VcsKey key) {
    final ContentRevision afterRevision = change.getAfterRevision();
    final ContentRevision beforeRevision = change.getBeforeRevision();
    if (afterRevision != null) {
      add(afterRevision.getFile(), change.getFileStatus(), key, beforeRevision == null ? VcsRevisionNumber.NULL : beforeRevision.getRevisionNumber());
    }
    if (beforeRevision != null) {
      if (afterRevision != null) {
        if (! Comparing.equal(beforeRevision.getFile(), afterRevision.getFile())) {
          add(beforeRevision.getFile(), FileStatus.DELETED, key, beforeRevision.getRevisionNumber());
        }
      } else {
        add(beforeRevision.getFile(), change.getFileStatus(), key, beforeRevision.getRevisionNumber());
      }
    }
  }

  /**
   * this method is called after each local changes refresh and collects all:
   * - paths that are new in local changes
   * - paths that are no more changed locally
   * - paths that were and are changed, but base revision has changed (ex. external update)
   * (for RemoteRevisionsCache and annotation listener)
   */
  public void getDelta(final ChangeListsIndexes newIndexes,
                       final Set<BaseRevision> toRemove,
                       Set<BaseRevision> toAdd,
                       Set<BeforeAfter<BaseRevision>> toModify) {
    // this is old
    final Set<FilePath> oldKeySet = newHashSet(myFileToVcs.keySet());
    final Set<FilePath> toRemoveSet = newHashSet(oldKeySet);
    final Set<FilePath> newKeySet = newIndexes.myFileToVcs.keySet();
    final Set<FilePath> toAddSet = newHashSet(newKeySet);
    toRemoveSet.removeAll(newKeySet);
    toAddSet.removeAll(oldKeySet);
    // those that modified
    oldKeySet.removeAll(toRemoveSet);

    for (FilePath s : toRemoveSet) {
      final Pair<VcsKey, VcsRevisionNumber> pair = myFileToVcs.get(s);
      toRemove.add(fromPairAndPath(s, pair));
    }
    for (FilePath s : toAddSet) {
      final Pair<VcsKey, VcsRevisionNumber> pair = newIndexes.myFileToVcs.get(s);
      toAdd.add(fromPairAndPath(s, pair));
    }
    for (FilePath s : oldKeySet) {
      final Pair<VcsKey, VcsRevisionNumber> old = myFileToVcs.get(s);
      final Pair<VcsKey, VcsRevisionNumber> newOne = newIndexes.myFileToVcs.get(s);
      assert old != null && newOne != null;
      if (! old.equals(newOne)) {
        toModify.add(new BeforeAfter<>(fromPairAndPath(s, old), fromPairAndPath(s, newOne)));
      }
    }
  }

  private static BaseRevision fromPairAndPath(FilePath s, Pair<VcsKey, VcsRevisionNumber> pair) {
    return new BaseRevision(pair.getFirst(), pair.getSecond(), s);
  }

  public List<BaseRevision> getAffectedFilesUnderVcs() {
    final List<BaseRevision> result = new ArrayList<>();
    for (Map.Entry<FilePath, Pair<VcsKey, VcsRevisionNumber>> entry : myFileToVcs.entrySet()) {
      final Pair<VcsKey, VcsRevisionNumber> value = entry.getValue();
      result.add(fromPairAndPath(entry.getKey(), value));
    }
    return result;
  }

  @NotNull
  public NavigableSet<FilePath> getAffectedPaths() {
    return Sets.unmodifiableNavigableSet(myFileToStatus.navigableKeySet());
  }
}
