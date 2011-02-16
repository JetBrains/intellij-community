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
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class ChangeListsIndexes {
  private final Map<String, FileStatus> myFileToStatus;
  private final Map<String, VcsKey> myFileToVcs;

  ChangeListsIndexes() {
    myFileToStatus = new HashMap<String, FileStatus>();
    myFileToVcs = new HashMap<String, VcsKey>();
  }

  ChangeListsIndexes(final ChangeListsIndexes idx) {
    myFileToStatus = new HashMap<String, FileStatus>(idx.myFileToStatus);
    myFileToVcs = new HashMap<String, VcsKey>(idx.myFileToVcs);
  }

  void add(final FilePath file, final FileStatus status, final VcsKey key) {
    final String fileKey = file.getIOFile().getAbsolutePath();
    myFileToStatus.put(fileKey, status);
    myFileToVcs.put(fileKey, key);
  }

  void remove(final FilePath file) {
    final String fileKey = file.getIOFile().getAbsolutePath();
    myFileToStatus.remove(fileKey);
    myFileToVcs.remove(fileKey);
  }

  public FileStatus getStatus(final VirtualFile file) {
    return myFileToStatus.get(new File(file.getPath()).getAbsolutePath());
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
      return myFileToVcs.get(fileKey);
    }
    return null;
  }

  private void addChangeToIdx(final Change change, final VcsKey key) {
    final ContentRevision afterRevision = change.getAfterRevision();
    if (afterRevision != null) {
      add(afterRevision.getFile(), change.getFileStatus(), key);
    }
    final ContentRevision beforeRevision = change.getBeforeRevision();
    if (beforeRevision != null) {
      if (afterRevision != null) {
        if (! Comparing.equal(beforeRevision.getFile(), afterRevision.getFile())) {
          add(beforeRevision.getFile(), FileStatus.DELETED, key);
        }
      } else {
        add(beforeRevision.getFile(), change.getFileStatus(), key);
      }
    }
  }

  public void getDelta(final ChangeListsIndexes newIndexes, final Set<Pair<String,VcsKey>> toRemove, Set<Pair<String,VcsKey>> toAdd) {
    // this is old
    final Set<String> oldKeySet = myFileToVcs.keySet();
    final Set<String> toRemoveSet = new HashSet<String>(oldKeySet);
    final Set<String> newKeySet = newIndexes.myFileToVcs.keySet();
    final Set<String> toAddSet = new HashSet<String>(newKeySet);
    toRemoveSet.removeAll(newKeySet);
    toAddSet.removeAll(oldKeySet);
    for (String s : toRemoveSet) {
      toRemove.add(new Pair<String, VcsKey>(s, myFileToVcs.get(s)));
    }
    for (String s : toAddSet) {
      toAdd.add(new Pair<String, VcsKey>(s, newIndexes.myFileToVcs.get(s)));
    }
  }

  public List<Pair<String, VcsKey>> getAffectedFilesUnderVcs() {
    final ArrayList<Pair<String, VcsKey>> result = new ArrayList<Pair<String, VcsKey>>();
    for (Map.Entry<String, VcsKey> entry : myFileToVcs.entrySet()) {
      result.add(new Pair<String, VcsKey>(entry.getKey(), entry.getValue()));
    }
    return result;
  }

  public List<File> getAffectedPaths() {
    final List<File> result = new ArrayList<File>(myFileToStatus.size());
    for (String path : myFileToStatus.keySet()) {
      result.add(new File(path));
    }
    return result;
  }
}
