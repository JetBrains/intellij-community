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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.BaseRevision;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BeforeAfter;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class ChangeListsIndexes {
  private final TreeMap<String, FileStatus> myFileToStatus;
  private final Map<String, Pair<VcsKey, VcsRevisionNumber>> myFileToVcs;

  ChangeListsIndexes() {
    myFileToStatus = new TreeMap<String, FileStatus>();
    myFileToVcs = new HashMap<String, Pair<VcsKey, VcsRevisionNumber>>();
  }

  ChangeListsIndexes(final ChangeListsIndexes idx) {
    myFileToStatus = new TreeMap<String, FileStatus>(idx.myFileToStatus);
    myFileToVcs = new HashMap<String, Pair<VcsKey, VcsRevisionNumber>>(idx.myFileToVcs);
  }

  void add(final FilePath file, final FileStatus status, final VcsKey key, VcsRevisionNumber number) {
    final String fileKey = file.getIOFile().getAbsolutePath();
    myFileToStatus.put(fileKey, status);
    myFileToVcs.put(fileKey, Pair.create(key, number));
  }

  void remove(final FilePath file) {
    final String fileKey = file.getIOFile().getAbsolutePath();
    myFileToStatus.remove(fileKey);
    myFileToVcs.remove(fileKey);
  }

  public FileStatus getStatus(final VirtualFile file) {
    return myFileToStatus.get(new File(file.getPath()).getAbsolutePath());
  }
  
  public FileStatus getStatus(final File file) {
    return myFileToStatus.get(file.getAbsolutePath());
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

  public VcsKey getVcsFor(final Change change) {
    VcsKey key = getVcsForRevision(change.getAfterRevision());
    if (key != null) return key;
    return getVcsForRevision(change.getBeforeRevision());
  }

  @Nullable
  private VcsKey getVcsForRevision(final ContentRevision revision) {
    if (revision != null) {
      final String fileKey = revision.getFile().getIOFile().getAbsolutePath();
      final Pair<VcsKey, VcsRevisionNumber> pair = myFileToVcs.get(fileKey);
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
    final Set<String> oldKeySet = new HashSet<String>(myFileToVcs.keySet());
    final Set<String> toRemoveSet = new HashSet<String>(oldKeySet);
    final Set<String> newKeySet = newIndexes.myFileToVcs.keySet();
    final Set<String> toAddSet = new HashSet<String>(newKeySet);
    toRemoveSet.removeAll(newKeySet);
    toAddSet.removeAll(oldKeySet);
    // those that modified
    oldKeySet.removeAll(toRemoveSet);

    for (String s : toRemoveSet) {
      final Pair<VcsKey, VcsRevisionNumber> pair = myFileToVcs.get(s);
      toRemove.add(fromPairAndPath(s, pair));
    }
    for (String s : toAddSet) {
      final Pair<VcsKey, VcsRevisionNumber> pair = newIndexes.myFileToVcs.get(s);
      toAdd.add(fromPairAndPath(s, pair));
    }
    for (String s : oldKeySet) {
      final Pair<VcsKey, VcsRevisionNumber> old = myFileToVcs.get(s);
      final Pair<VcsKey, VcsRevisionNumber> newOne = newIndexes.myFileToVcs.get(s);
      assert old != null && newOne != null;
      if (! old.equals(newOne)) {
        toModify.add(new BeforeAfter<BaseRevision>(fromPairAndPath(s, old), fromPairAndPath(s, newOne)));
      }
    }
  }

  private BaseRevision fromPairAndPath(String s, Pair<VcsKey, VcsRevisionNumber> pair) {
    return new BaseRevision(pair.getFirst(), pair.getSecond(), s);
  }

  public List<BaseRevision> getAffectedFilesUnderVcs() {
    final List<BaseRevision> result = new ArrayList<BaseRevision>();
    for (Map.Entry<String, Pair<VcsKey, VcsRevisionNumber>> entry : myFileToVcs.entrySet()) {
      final Pair<VcsKey, VcsRevisionNumber> value = entry.getValue();
      result.add(fromPairAndPath(entry.getKey(), value));
    }
    return result;
  }

  public SortedSet<String> getAffectedPaths() {
    return Collections.unmodifiableSortedSet((SortedSet<String>)myFileToStatus.keySet());
  }
}
