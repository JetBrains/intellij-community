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

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VcsLogPathsIndex extends VcsLogFullDetailsIndex {
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
          disposableParent);

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
    Set<Integer> result = ContainerUtil.newHashSet();
    for (FilePath path : paths) {
      result.add(myPathsIndexer.getPathId(path));
    }
    return getCommitsWithAnyKey(result);
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

  private static class PathsIndexer implements DataIndexer<Integer, Void, VcsFullCommitDetails> {
    @NotNull private final PersistentEnumeratorBase<String> myPathsEnumerator;
    @NotNull private final Set<VirtualFile> myRoots;

    private PathsIndexer(@NotNull PersistentEnumeratorBase<String> enumerator, @NotNull Set<VirtualFile> roots) {
      myPathsEnumerator = enumerator;
      myRoots = roots;
    }

    @NotNull
    @Override
    public Map<Integer, Void> map(@NotNull VcsFullCommitDetails inputData) {
      Map<Integer, Void> result = new THashMap<>();

      Collection<Change> changes = inputData.getChanges();
      for (Change change : changes) {
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
      return result;
    }

    @NotNull
    private Collection<Integer> getPathIds(@NotNull FilePath path) {
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
}
