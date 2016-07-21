/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.*;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.util.PersistentUtil;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VcsLogPathsIndex extends VcsLogFullDetailsIndex<Integer> {
  private static final Logger LOG = Logger.getInstance(VcsLogPathsIndex.class);
  private static final String NAME = "paths";
  private static final int VERSION = 0;
  private static final int VALUE = 239;

  @NotNull private final PersistentHashMap<Integer, Integer> myEmptyCommits;
  @NotNull private final PathsIndexer myPathsIndexer;

  public VcsLogPathsIndex(@NotNull String logId,
                          @NotNull Set<VirtualFile> roots,
                          @NotNull Disposable disposableParent) throws IOException {
    super(logId, NAME, VERSION, new PathsIndexer(
            PersistentUtil.createPersistentEnumerator(EnumeratorStringDescriptor.INSTANCE, "index-paths-ids", logId, VERSION), roots),
          new NullableIntKeyDescriptor(), disposableParent);

    myEmptyCommits = PersistentUtil.createPersistentHashMap(EnumeratorIntegerDescriptor.INSTANCE, "index-no-" + NAME, logId, VERSION);
    myPathsIndexer = (PathsIndexer)myIndexer;
  }

  @Override
  protected void onNotIndexableCommit(int commit) throws StorageException {
    try {
      myEmptyCommits.put(commit, VALUE);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public boolean isIndexed(int commit) throws IOException {
    return super.isIndexed(commit) || myEmptyCommits.containsMapping(commit);
  }

  @Override
  public void flush() throws StorageException {
    super.flush();
    myEmptyCommits.force();
    myPathsIndexer.getPathsEnumerator().force();
  }

  public TIntHashSet getCommitsForPaths(@NotNull Collection<FilePath> paths) throws IOException, StorageException {
    Set<Integer> allPathIds = ContainerUtil.newHashSet();
    for (FilePath path : paths) {
      allPathIds.add(myPathsIndexer.getPathId(path));
    }

    TIntHashSet result = new TIntHashSet();
    Set<Integer> renames = allPathIds;
    while (!renames.isEmpty()) {
      renames = addCommitsAndGetRenames(renames, allPathIds, result);
      allPathIds.addAll(renames);
    }

    return result;
  }

  @NotNull
  public Set<Integer> addCommitsAndGetRenames(@NotNull Set<Integer> newPathIds,
                                              @NotNull Set<Integer> allPathIds,
                                              @NotNull TIntHashSet commits)
    throws StorageException {
    Set<Integer> renames = ContainerUtil.newHashSet();
    for (Integer key : newPathIds) {
      iterateCommitIdsAndValues(key, (value, commit) -> {
        commits.add(commit);
        if (value != null && !allPathIds.contains(value)) {
          renames.add(value);
        }
      });
    }
    return renames;
  }

  @Override
  public void dispose() {
    super.dispose();
    try {
      myEmptyCommits.close();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    try {
      myPathsIndexer.getPathsEnumerator().close();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  private static class PathsIndexer implements DataIndexer<Integer, Integer, VcsFullCommitDetails> {
    @NotNull private final PersistentEnumeratorBase<String> myPathsEnumerator;
    @NotNull private final Set<VirtualFile> myRoots;

    private PathsIndexer(@NotNull PersistentEnumeratorBase<String> enumerator, @NotNull Set<VirtualFile> roots) {
      myPathsEnumerator = enumerator;
      myRoots = roots;
    }

    @NotNull
    @Override
    public Map<Integer, Integer> map(@NotNull VcsFullCommitDetails inputData) {
      Map<Integer, Integer> result = new THashMap<>();

      Collection<Change> changes = inputData.getChanges();
      for (Change change : changes) {
        if (change.getType().equals(Change.Type.MOVED)) {
          putMove(result, change);
        }
        else {
          putChange(result, change);
        }
      }
      return result;
    }

    private void putMove(@NotNull Map<Integer, Integer> result, @NotNull Change change) {
      ContentRevision beforeRevision = change.getBeforeRevision();
      ContentRevision afterRevision = change.getAfterRevision();

      assert beforeRevision != null && afterRevision != null;

      List<Integer> beforeIds = getPathIds(beforeRevision.getFile());
      if (beforeIds.size() > 1) {
        for (Integer pathId : ContainerUtil.subList(beforeIds, 1)) {
          result.put(pathId, null);
        }
      }
      List<Integer> afterIds = getPathIds(afterRevision.getFile());
      if (afterIds.size() > 1) {
        for (Integer pathId : ContainerUtil.subList(afterIds, 1)) {
          result.put(pathId, null);
        }
      }

      result.put(beforeIds.get(0), afterIds.get(0));
      result.put(afterIds.get(0), beforeIds.get(0));
    }

    private void putChange(@NotNull Map<Integer, Integer> result, @NotNull Change change) {
      ContentRevision beforeRevision = change.getBeforeRevision();
      if (beforeRevision != null) {
        for (Integer pathId : getPathIds(beforeRevision.getFile())) {
          result.put(pathId, null);
        }
      }

      ContentRevision afterRevision = change.getAfterRevision();
      if (afterRevision != null) {
        for (Integer pathId : getPathIds(afterRevision.getFile())) {
          result.put(pathId, null);
        }
      }
    }

    @NotNull
    private List<Integer> getPathIds(@NotNull FilePath path) {
      List<Integer> result = ContainerUtil.newArrayList();
      try {
        while (path != null) {
          result.add(getPathId(path));
          VirtualFile file = path.getVirtualFile();
          if (file != null && myRoots.contains(file)) break;

          path = path.getParentPath();
        }
      }
      catch (IOException e) {
        e.printStackTrace(); // TODO ?
      }
      return result;
    }

    public int getPathId(@NotNull FilePath path) throws IOException {
      return myPathsEnumerator.enumerate(path.getPath());
    }

    @NotNull
    public PersistentEnumeratorBase<String> getPathsEnumerator() {
      return myPathsEnumerator;
    }
  }

  private static class NullableIntKeyDescriptor implements DataExternalizer<Integer> {
    @Override
    public void save(@NotNull DataOutput out, Integer value) throws IOException {
      if (value == null) {
        out.writeBoolean(false);
      }
      else {
        out.writeBoolean(true);
        out.writeInt(value);
      }
    }

    @Override
    public Integer read(@NotNull DataInput in) throws IOException {
      if (in.readBoolean()) {
        return in.readInt();
      }
      return null;
    }
  }
}
