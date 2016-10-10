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
import com.intellij.vcs.log.impl.FatalErrorConsumer;
import com.intellij.vcs.log.impl.VcsChangesLazilyParsedDetails;
import com.intellij.vcs.log.util.PersistentUtil;
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

import static com.intellij.util.containers.ContainerUtil.newTroveSet;

public class VcsLogPathsIndex extends VcsLogFullDetailsIndex<Integer> {
  private static final Logger LOG = Logger.getInstance(VcsLogPathsIndex.class);
  private static final String NAME = "paths";
  private static final int VALUE = 239;

  @NotNull private final PersistentHashMap<Integer, Integer> myEmptyCommits;
  @NotNull private final PathsIndexer myPathsIndexer;

  public VcsLogPathsIndex(@NotNull String logId,
                          @NotNull Set<VirtualFile> roots,
                          @NotNull FatalErrorConsumer fatalErrorConsumer,
                          @NotNull Disposable disposableParent) throws IOException {
    super(logId, NAME, VcsLogPersistentIndex.getVersion(), new PathsIndexer(createPathsEnumerator(logId), roots),
          new NullableIntKeyDescriptor(), disposableParent);

    myEmptyCommits = PersistentUtil.createPersistentHashMap(EnumeratorIntegerDescriptor.INSTANCE, "index-no-" + NAME, logId,
                                                            VcsLogPersistentIndex.getVersion());
    myPathsIndexer = (PathsIndexer)myIndexer;
    myPathsIndexer.setFatalErrorConsumer(e -> {
      fatalErrorConsumer.consume(this, e);
      markCorrupted();
    });
  }

  @NotNull
  private static PersistentEnumeratorBase<String> createPathsEnumerator(@NotNull String logId) throws IOException {
    int version = VcsLogPersistentIndex.getVersion();
    final File storageFile = PersistentUtil.getStorageFile("index-paths-ids", logId, version);

    PersistentBTreeEnumerator<String> enumerator = IOUtil.openCleanOrResetBroken(
      () -> new PersistentBTreeEnumerator<>(storageFile, SystemInfo.isFileSystemCaseSensitive ? EnumeratorStringDescriptor.INSTANCE
                                                                                              : new ToLowerCaseStringDescriptor(),
                                            Page.PAGE_SIZE, null, version),
      () -> {
        IOUtil.deleteAllFilesStartingWith(getStorageFile(INDEX + NAME, logId, version));
        IOUtil.deleteAllFilesStartingWith(getStorageFile(INDEX_INPUTS + NAME, logId, version));
        IOUtil.deleteAllFilesStartingWith(storageFile);
      });
    if (enumerator == null) throw new IOException("Can not create enumerator " + NAME + " for " + logId);
    return enumerator;
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
      allPathIds.add(myPathsIndexer.myPathsEnumerator.enumerate(path.getPath()));
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

  @Override
  public void markCorrupted() {
    super.markCorrupted();
    myEmptyCommits.markCorrupted();
  }

  @NotNull
  public String getPathInfo(int commit) throws IOException {
    if (myEmptyCommits.containsMapping(commit)) {
      return "No paths";
    }
    Collection<Integer> keys = getKeysForCommit(commit);
    assert keys != null;
    StringBuilder builder = new StringBuilder();
    for (int key : keys) {
      builder.append(myPathsIndexer.getPathsEnumerator().valueOf(key)).append("\n");
    }
    return builder.toString();
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
      if (inputData instanceof VcsChangesLazilyParsedDetails) {
        changedPaths = ((VcsChangesLazilyParsedDetails)inputData).getModifiedPaths();
        moves = ((VcsChangesLazilyParsedDetails)inputData).getRenamedPaths();
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
