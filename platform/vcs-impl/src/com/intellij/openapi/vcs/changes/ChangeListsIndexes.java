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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BeforeAfter;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.HashSet;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ChangeListsIndexes {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangeListsIndexes");
  private final Map<FilePath, Data> myMap;
  private final TreeSet<FilePath> myAffectedPaths;
  private final Set<Change> myChanges;

  public ChangeListsIndexes() {
    myMap = new HashMap<>();
    myAffectedPaths = new TreeSet<>(HierarchicalFilePathComparator.SYSTEM_CASE_SENSITIVE);
    myChanges = new HashSet<>();
  }

  public ChangeListsIndexes(@NotNull ChangeListsIndexes idx) {
    myMap = new HashMap<>(idx.myMap);
    myAffectedPaths = new TreeSet<>(idx.myAffectedPaths);
    myChanges = new HashSet<>(idx.myChanges);
  }

  public void copyFrom(@NotNull ChangeListsIndexes idx) {
    myMap.clear();
    myAffectedPaths.clear();
    myChanges.clear();

    myMap.putAll(idx.myMap);
    myAffectedPaths.addAll(idx.myAffectedPaths);
    myChanges.addAll(idx.myChanges);
  }


  private void add(@NotNull FilePath file, @NotNull FileStatus status, @Nullable VcsKey key, @NotNull VcsRevisionNumber number) {
    myMap.put(file, new Data(status, key, number));
    myAffectedPaths.add(file);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Set status " + status + " for " + file);
    }
  }

  private void remove(final FilePath file) {
    myMap.remove(file);
    myAffectedPaths.remove(file);
  }

  @Nullable
  public FileStatus getStatus(@NotNull VirtualFile file) {
    return getStatus(VcsUtil.getFilePath(file));
  }

  @Nullable
  public FileStatus getStatus(@NotNull FilePath file) {
    Data data = myMap.get(file);
    return data != null ? data.status : null;
  }

  public void changeAdded(@NotNull Change change, VcsKey key) {
    myChanges.add(change);

    ContentRevision afterRevision = change.getAfterRevision();
    ContentRevision beforeRevision = change.getBeforeRevision();

    if (beforeRevision != null && afterRevision != null) {
      add(afterRevision.getFile(), change.getFileStatus(), key, beforeRevision.getRevisionNumber());

      if (!Comparing.equal(beforeRevision.getFile(), afterRevision.getFile())) {
        add(beforeRevision.getFile(), FileStatus.DELETED, key, beforeRevision.getRevisionNumber());
      }
    }
    else if (afterRevision != null) {
      add(afterRevision.getFile(), change.getFileStatus(), key, VcsRevisionNumber.NULL);
    }
    else if (beforeRevision != null) {
      add(beforeRevision.getFile(), change.getFileStatus(), key, beforeRevision.getRevisionNumber());
    }
  }

  public void changeRemoved(@NotNull Change change) {
    myChanges.remove(change);

    ContentRevision afterRevision = change.getAfterRevision();
    ContentRevision beforeRevision = change.getBeforeRevision();

    if (afterRevision != null) {
      remove(afterRevision.getFile());
    }
    if (beforeRevision != null) {
      remove(beforeRevision.getFile());
    }
  }

  @NotNull
  public Set<Change> getChanges() {
    return myChanges;
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
      Data data = myMap.get(revision.getFile());
      return data != null ? data.vcsKey : null;
    }
    return null;
  }

  /**
   * this method is called after each local changes refresh and collects all:
   * - paths that are new in local changes
   * - paths that are no more changed locally
   * - paths that were and are changed, but base revision has changed (ex. external update)
   * (for RemoteRevisionsCache and annotation listener)
   */
  public void getDelta(ChangeListsIndexes newIndexes,
                       Set<BaseRevision> toRemove,
                       Set<BaseRevision> toAdd,
                       Set<BeforeAfter<BaseRevision>> toModify) {
    Map<FilePath, Data> oldMap = myMap;
    Map<FilePath, Data> newMap = newIndexes.myMap;

    for (Map.Entry<FilePath, Data> entry : oldMap.entrySet()) {
      FilePath s = entry.getKey();
      Data oldData = entry.getValue();
      Data newData = newMap.get(s);

      if (newData != null) {
        if (!oldData.sameRevisions(newData)) {
          toModify.add(new BeforeAfter<>(createBaseRevision(s, oldData), createBaseRevision(s, newData)));
        }
      }
      else {
        toRemove.add(createBaseRevision(s, oldData));
      }
    }

    for (Map.Entry<FilePath, Data> entry : newMap.entrySet()) {
      FilePath s = entry.getKey();
      Data newData = entry.getValue();

      if (!oldMap.containsKey(s)) {
        toAdd.add(createBaseRevision(s, newData));
      }
    }
  }

  @NotNull
  public ThreeState haveChangesUnder(@NotNull FilePath dir) {
    FilePath changeCandidate = myAffectedPaths.ceiling(dir);
    if (changeCandidate == null) return ThreeState.NO;
    return FileUtil.isAncestorThreeState(dir.getPath(), changeCandidate.getPath(), false);
  }

  private static BaseRevision createBaseRevision(@NotNull FilePath path, @NotNull Data data) {
    return new BaseRevision(data.vcsKey, data.revision, path);
  }

  public void clear() {
    myMap.clear();
    myAffectedPaths.clear();
  }

  @NotNull
  public Set<FilePath> getAffectedPaths() {
    return Collections.unmodifiableSet(myMap.keySet());
  }

  private static class Data {
    @NotNull public final FileStatus status;
    public final VcsKey vcsKey;
    @NotNull public final VcsRevisionNumber revision;

    public Data(@NotNull FileStatus status, VcsKey vcsKey, @NotNull VcsRevisionNumber revision) {
      this.status = status;
      this.vcsKey = vcsKey;
      this.revision = revision;
    }

    public boolean sameRevisions(@NotNull Data data) {
      return Comparing.equal(vcsKey, data.vcsKey) && Comparing.equal(revision, data.revision);
    }
  }
}
