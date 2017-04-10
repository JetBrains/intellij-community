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
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.*;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcs.log.impl.VcsIndexableDetails;
import com.intellij.vcs.log.util.PersistentUtil;
import com.intellij.vcsUtil.VcsUtil;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.ObjIntConsumer;

import static com.intellij.util.containers.ContainerUtil.newTroveSet;
import static com.intellij.vcs.log.data.index.VcsLogPersistentIndex.getVersion;

public class VcsLogPathsIndex extends VcsLogFullDetailsIndex<Integer> {
  private static final Logger LOG = Logger.getInstance(VcsLogPathsIndex.class);
  public static final String PATHS = "paths";
  public static final String INDEX_PATHS_IDS = "paths-ids";

  @NotNull private final PathsIndexer myPathsIndexer;

  public VcsLogPathsIndex(@NotNull String logId,
                          @NotNull Set<VirtualFile> roots,
                          @NotNull FatalErrorHandler fatalErrorHandler,
                          @NotNull Disposable disposableParent) throws IOException {
    super(logId, PATHS, getVersion(), new PathsIndexer(createPathsEnumerator(logId), roots),
          new NullableIntKeyDescriptor(), fatalErrorHandler, disposableParent);

    myPathsIndexer = (PathsIndexer)myIndexer;
    myPathsIndexer.setFatalErrorConsumer(e -> fatalErrorHandler.consume(this, e));
  }

  @NotNull
  private static PersistentEnumeratorBase<String> createPathsEnumerator(@NotNull String logId) throws IOException {
    File storageFile = PersistentUtil.getStorageFile(INDEX, INDEX_PATHS_IDS, logId, getVersion(), true);
    return new PersistentBTreeEnumerator<>(storageFile, SystemInfo.isFileSystemCaseSensitive ? EnumeratorStringDescriptor.INSTANCE
                                                                                             : new ToLowerCaseStringDescriptor(),
                                           Page.PAGE_SIZE, null, getVersion());
  }

  @Override
  public void flush() throws StorageException {
    super.flush();
    myPathsIndexer.getPathsEnumerator().force();
  }

  @NotNull
  public TIntHashSet getCommitsForPaths(@NotNull Collection<FilePath> paths) throws IOException, StorageException {
    Set<Integer> allPathIds = getPathIds(paths);

    TIntHashSet result = new TIntHashSet();
    Set<Integer> renames = allPathIds;
    while (!renames.isEmpty()) {
      renames = addCommitsAndGetRenames(renames, allPathIds, result);
      allPathIds.addAll(renames);
    }

    return result;
  }

  @NotNull
  private Set<Integer> getPathIds(@NotNull Collection<FilePath> paths) throws IOException {
    Set<Integer> allPathIds = ContainerUtil.newHashSet();
    for (FilePath path : paths) {
      allPathIds.add(myPathsIndexer.myPathsEnumerator.enumerate(path.getPath()));
    }
    return allPathIds;
  }

  @NotNull
  public Set<FilePath> getFileNames(@NotNull FilePath path, int commit) throws IOException, StorageException {
    int startId = myPathsIndexer.myPathsEnumerator.enumerate(path.getPath());

    Set<Integer> startIds = ContainerUtil.newHashSet();
    startIds.add(startId);
    Set<Integer> allIds = ContainerUtil.newHashSet(startIds);
    Set<Integer> newIds = ContainerUtil.newHashSet();

    Set<Integer> resultIds = ContainerUtil.newHashSet();

    outer:
    while (!startIds.isEmpty()) {
      for (int currentPathId : startIds) {
        boolean foundCommit = !iterateCommitIdsAndValues(currentPathId, (renamedPathId, commitId) -> {
          if (commitId == commit) {
            resultIds.add(currentPathId);
            if (renamedPathId != null) resultIds.add(renamedPathId);
            return false;
          }
          if (renamedPathId != null && !allIds.contains(renamedPathId)) {
            newIds.add(renamedPathId);
          }
          return true;
        });
        if (foundCommit) break outer;
      }
      startIds = ContainerUtil.newHashSet(newIds);
      allIds.addAll(startIds);
      newIds.clear();
    }

    Set<FilePath> result = ContainerUtil.newHashSet();
    for (Integer id : resultIds) {
      result.add(VcsUtil.getFilePath(myPathsIndexer.myPathsEnumerator.valueOf(id)));
    }
    return result;
  }

  public void iterateCommits(@NotNull Collection<FilePath> paths, @NotNull ObjIntConsumer<Couple<FilePath>> consumer)
    throws IOException, StorageException {

    Set<Integer> startIds = getPathIds(paths);
    Set<Integer> allIds = ContainerUtil.newHashSet(startIds);
    Set<Integer> newIds = ContainerUtil.newHashSet();
    while (!startIds.isEmpty()) {
      for (int currentPathId : startIds) {
        FilePath currentPath = VcsUtil.getFilePath(myPathsIndexer.myPathsEnumerator.valueOf(currentPathId));
        iterateCommitIdsAndValues(currentPathId, (renamedPathId, commitId) -> {
          FilePath renamedPath = null;
          if (renamedPathId != null) {
            if (!allIds.contains(renamedPathId)) {
              newIds.add(renamedPathId);
            }
            try {
              renamedPath = VcsUtil.getFilePath(myPathsIndexer.myPathsEnumerator.valueOf(renamedPathId));
            }
            catch (IOException e) {
              LOG.error(e);
            }
          }
          consumer.accept(Couple.of(currentPath, renamedPath), commitId);
        });
      }
      startIds = ContainerUtil.newHashSet(newIds);
      allIds.addAll(startIds);
      newIds.clear();
    }
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
      myPathsIndexer.getPathsEnumerator().close();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  private static class PathsIndexer implements DataIndexer<Integer, Integer, VcsFullCommitDetails> {
    @NotNull private final PersistentEnumeratorBase<String> myPathsEnumerator;
    @NotNull private final Set<String> myRoots;
    @NotNull private Consumer<Exception> myFatalErrorConsumer = LOG::error;

    private PathsIndexer(@NotNull PersistentEnumeratorBase<String> enumerator, @NotNull Set<VirtualFile> roots) {
      myPathsEnumerator = enumerator;
      myRoots = newTroveSet(FileUtil.PATH_HASHING_STRATEGY);
      for (VirtualFile root : roots) {
        myRoots.add(root.getPath());
      }
    }

    public void setFatalErrorConsumer(@NotNull Consumer<Exception> fatalErrorConsumer) {
      myFatalErrorConsumer = fatalErrorConsumer;
    }

    @NotNull
    @Override
    public Map<Integer, Integer> map(@NotNull VcsFullCommitDetails inputData) {
      Map<Integer, Integer> result = new THashMap<>();


      Collection<Couple<String>> moves;
      Collection<String> changedPaths;
      if (inputData instanceof VcsIndexableDetails) {
        changedPaths = ((VcsIndexableDetails)inputData).getModifiedPaths(0);
        moves = ((VcsIndexableDetails)inputData).getRenamedPaths(0);
      }
      else {
        moves = ContainerUtil.newHashSet();
        changedPaths = ContainerUtil.newHashSet();
        for (Change change : inputData.getChanges()) {
          if (change.getAfterRevision() != null) changedPaths.add(change.getAfterRevision().getFile().getPath());
          if (change.getBeforeRevision() != null) changedPaths.add(change.getBeforeRevision().getFile().getPath());
          if (change.getType().equals(Change.Type.MOVED)) {
            moves.add(Couple.of(change.getBeforeRevision().getFile().getPath(), change.getAfterRevision().getFile().getPath()));
          }
        }
      }

      getParentPaths(changedPaths).forEach(changedPath -> {
        try {
          result.put(myPathsEnumerator.enumerate(changedPath), null);
        }
        catch (IOException e) {
          myFatalErrorConsumer.consume(e);
        }
      });
      moves.forEach(renamedPaths -> {
        try {
          int beforeId = myPathsEnumerator.enumerate(renamedPaths.first);
          int afterId = myPathsEnumerator.enumerate(renamedPaths.second);

          result.put(beforeId, afterId);
          result.put(afterId, beforeId);
        }
        catch (IOException e) {
          myFatalErrorConsumer.consume(e);
        }
      });

      return result;
    }

    @NotNull
    private Collection<String> getParentPaths(@NotNull Collection<String> paths) {
      Set<String> result = ContainerUtil.newHashSet();
      for (String path : paths) {
        while (!path.isEmpty() && !result.contains(path)) {
          result.add(path);
          if (myRoots.contains(path)) break;

          path = PathUtil.getParentPath(path);
        }
      }
      return result;
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

  private static class ToLowerCaseStringDescriptor implements KeyDescriptor<String> {
    @Override
    public int getHashCode(String value) {
      return CaseInsensitiveStringHashingStrategy.INSTANCE.computeHashCode(value);
    }

    @Override
    public boolean isEqual(String val1, String val2) {
      return CaseInsensitiveStringHashingStrategy.INSTANCE.equals(val1, val2);
    }

    @Override
    public void save(@NotNull DataOutput out, String value) throws IOException {
      IOUtil.writeUTF(out, value.toLowerCase());
    }

    @Override
    public String read(@NotNull DataInput in) throws IOException {
      return IOUtil.readUTF(in);
    }
  }
}
