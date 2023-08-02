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
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.BeforeAfter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class ChangeListsIndexes {
  private static final Logger LOG = Logger.getInstance(ChangeListsIndexes.class);

  private Map<FilePath, Data> myMap;
  private Set<Change> myChanges;

  ChangeListsIndexes() {
    myMap = new HashMap<>();
    myChanges = new HashSet<>();
  }

  public void copyFrom(@NotNull ChangeListsIndexes idx) {
    myMap = new HashMap<>(idx.myMap);
    myChanges = new HashSet<>(idx.myChanges);
  }

  public void clear() {
    myMap = new HashMap<>();
    myChanges = new HashSet<>();
  }


  private void add(@NotNull FilePath file,
                   @NotNull Change change,
                   @NotNull FileStatus status,
                   @Nullable AbstractVcs key,
                   @NotNull VcsRevisionNumber number) {
    myMap.put(file, new Data(status, change, key, number));
    if (LOG.isDebugEnabled()) {
      LOG.debug("Set status " + status + " for " + file);
    }
  }

  private void remove(@NotNull FilePath file) {
    myMap.remove(file);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Clear status for " + file);
    }
  }

  @Nullable
  public Change getChange(@NotNull FilePath file) {
    Data data = myMap.get(file);
    return data != null ? data.change : null;
  }

  @Nullable
  public FileStatus getStatus(@NotNull FilePath file) {
    Data data = myMap.get(file);
    return data != null ? data.status : null;
  }

  public void changeAdded(@NotNull Change change, @Nullable AbstractVcs key) {
    myChanges.add(change);

    ContentRevision afterRevision = change.getAfterRevision();
    ContentRevision beforeRevision = change.getBeforeRevision();

    if (beforeRevision != null && afterRevision != null) {
      add(afterRevision.getFile(), change, change.getFileStatus(), key, beforeRevision.getRevisionNumber());

      if (!Objects.equals(beforeRevision.getFile(), afterRevision.getFile())) {
        add(beforeRevision.getFile(), change, FileStatus.DELETED, key, beforeRevision.getRevisionNumber());
      }
    }
    else if (afterRevision != null) {
      add(afterRevision.getFile(), change, change.getFileStatus(), key, VcsRevisionNumber.NULL);
    }
    else if (beforeRevision != null) {
      add(beforeRevision.getFile(), change, change.getFileStatus(), key, beforeRevision.getRevisionNumber());
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
  public AbstractVcs getVcsFor(@NotNull Change change) {
    AbstractVcs vcs = getVcsForRevision(change.getAfterRevision());
    if (vcs != null) return vcs;
    return getVcsForRevision(change.getBeforeRevision());
  }

  @Nullable
  private AbstractVcs getVcsForRevision(@Nullable ContentRevision revision) {
    if (revision != null) {
      Data data = myMap.get(revision.getFile());
      return data != null ? data.vcs : null;
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
                       Set<? super BaseRevision> toRemove,
                       Set<? super BaseRevision> toAdd,
                       Set<? super BeforeAfter<BaseRevision>> toModify) {
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

  private static BaseRevision createBaseRevision(@NotNull FilePath path, @NotNull Data data) {
    return new BaseRevision(data.vcs, data.revision, path);
  }

  @NotNull
  public Set<FilePath> getAffectedPaths() {
    return myMap.keySet();
  }

  private static class Data {
    @NotNull public final FileStatus status;
    @NotNull public final Change change;
    @Nullable public final AbstractVcs vcs;
    @NotNull public final VcsRevisionNumber revision;

    Data(@NotNull FileStatus status,
         @NotNull Change change,
         @Nullable AbstractVcs vcs,
         @NotNull VcsRevisionNumber revision) {
      this.status = status;
      this.change = change;
      this.vcs = vcs;
      this.revision = revision;
    }

    public boolean sameRevisions(@NotNull Data data) {
      return Comparing.equal(vcs, data.vcs) && Comparing.equal(revision, data.revision);
    }
  }
}
