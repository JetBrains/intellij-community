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
  private final TreeMap<FilePath, Data> myMap;

  public ChangeListsIndexes() {
    myMap = new TreeMap<>(HierarchicalFilePathComparator.SYSTEM_CASE_SENSITIVE);
  }

  public ChangeListsIndexes(@NotNull ChangeListsIndexes idx) {
    myMap = new TreeMap<>(idx.myMap);
  }

  private void add(@NotNull FilePath file, @NotNull FileStatus status, @Nullable VcsKey key, @NotNull VcsRevisionNumber number) {
    myMap.put(file, new Data(status, key, number));
    if (LOG.isDebugEnabled()) {
      LOG.debug("Set status " + status + " for " + file);
    }
  }

  public void remove(final FilePath file) {
    myMap.remove(file);
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
    ContentRevision afterRevision = change.getAfterRevision();
    ContentRevision beforeRevision = change.getBeforeRevision();

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
    TreeMap<FilePath, Data> oldMap = myMap;
    TreeMap<FilePath, Data> newMap = newIndexes.myMap;
    Set<FilePath> oldFiles = oldMap.keySet();
    Set<FilePath> newFiles = newMap.keySet();

    final Set<FilePath> toRemoveSet = newHashSet(oldFiles);
    toRemoveSet.removeAll(newFiles);

    final Set<FilePath> toAddSet = newHashSet(newFiles);
    toAddSet.removeAll(oldFiles);

    final Set<FilePath> toModifySet = newHashSet(oldFiles);
    toModifySet.removeAll(toRemoveSet);

    for (FilePath s : toRemoveSet) {
      final Data data = oldMap.get(s);
      toRemove.add(createBaseRevision(s, data));
    }
    for (FilePath s : toAddSet) {
      final Data data = newMap.get(s);
      toAdd.add(createBaseRevision(s, data));
    }
    for (FilePath s : toModifySet) {
      final Data oldData = oldMap.get(s);
      final Data newData = newMap.get(s);
      assert oldData != null && newData != null;
      if (!oldData.sameRevisions(newData)) {
        toModify.add(new BeforeAfter<>(createBaseRevision(s, oldData), createBaseRevision(s, newData)));
      }
    }
  }

  private static BaseRevision createBaseRevision(@NotNull FilePath path, @NotNull Data data) {
    return new BaseRevision(data.vcsKey, data.revision, path);
  }

  @NotNull
  public List<BaseRevision> getAffectedFilesUnderVcs() {
    final List<BaseRevision> result = new ArrayList<>();
    for (Map.Entry<FilePath, Data> entry : myMap.entrySet()) {
      final Data value = entry.getValue();
      result.add(createBaseRevision(entry.getKey(), value));
    }
    return result;
  }

  public void clear() {
    myMap.clear();
  }

  @NotNull
  public NavigableSet<FilePath> getAffectedPaths() {
    return Sets.unmodifiableNavigableSet(myMap.navigableKeySet());
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
