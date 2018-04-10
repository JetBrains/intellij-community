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
import com.intellij.openapi.util.Pair;
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
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.ObjIntConsumer;

import static com.intellij.util.containers.ContainerUtil.newTroveSet;
import static com.intellij.vcs.log.data.index.VcsLogPersistentIndex.getVersion;

public class VcsLogPathsIndex extends VcsLogFullDetailsIndex<List<VcsLogPathsIndex.ChangeData>> {
  private static final Logger LOG = Logger.getInstance(VcsLogPathsIndex.class);
  public static final String PATHS = "paths";
  public static final String INDEX_PATHS_IDS = "paths-ids";

  @NotNull private final PathsIndexer myPathsIndexer;

  public VcsLogPathsIndex(@NotNull String logId,
                          @NotNull Set<VirtualFile> roots,
                          @NotNull FatalErrorHandler fatalErrorHandler,
                          @NotNull Disposable disposableParent) throws IOException {
    super(logId, PATHS, getVersion(), new PathsIndexer(createPathsEnumerator(logId), roots),
          new ChangeDataListKeyDescriptor(), fatalErrorHandler, disposableParent);

    myPathsIndexer = (PathsIndexer)myIndexer;
    myPathsIndexer.setFatalErrorConsumer(e -> fatalErrorHandler.consume(this, e));
  }

  @NotNull
  private static PersistentEnumeratorBase<String> createPathsEnumerator(@NotNull String logId) throws IOException {
    File storageFile = PersistentUtil.getStorageFile(INDEX, INDEX_PATHS_IDS, logId, getVersion());
    return new PersistentBTreeEnumerator<>(storageFile, SystemInfo.isFileSystemCaseSensitive ? EnumeratorStringDescriptor.INSTANCE
                                                                                             : new ToLowerCaseStringDescriptor(),
                                           Page.PAGE_SIZE, null, getVersion());
  }

  @Nullable
  public String getPath(int pathId) {
    try {
      return myPathsIndexer.getPathsEnumerator().valueOf(pathId);
    }
    catch (IOException e) {
      myPathsIndexer.myFatalErrorConsumer.consume(e);
    }
    return null;
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
        boolean foundCommit = !iterateCommitIdsAndValues(currentPathId, (changesList, commitId) -> {
          Set<Integer> otherNames = getOtherNames(changesList);
          if (commitId == commit) {
            resultIds.add(currentPathId);
            resultIds.addAll(otherNames);
            return false;
          }
          for (Integer otherPath : otherNames) {
            if (!allIds.contains(otherPath)) {
              newIds.add(otherPath);
            }
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

  public void iterateCommits(@NotNull FilePath path, @NotNull ObjIntConsumer<Pair<FilePath, List<ChangeData>>> consumer)
    throws IOException, StorageException {

    Set<Integer> startIds = getPathIds(Collections.singleton(path));
    Set<Integer> allIds = ContainerUtil.newHashSet(startIds);
    Set<Integer> newIds = ContainerUtil.newHashSet();
    while (!startIds.isEmpty()) {
      for (int currentPathId : startIds) {
        FilePath currentPath = VcsUtil.getFilePath(myPathsIndexer.myPathsEnumerator.valueOf(currentPathId));
        iterateCommitIdsAndValues(currentPathId, (changesList, commitId) -> {
          Set<Integer> otherNames = getOtherNames(changesList);
          for (int renamed : otherNames) {
            if (!allIds.contains(renamed)) {
              newIds.add(renamed);
            }
          }

          consumer.accept(Pair.create(currentPath, changesList), commitId);
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
        renames.addAll(ContainerUtil.filter(getOtherNames(value), r -> !allPathIds.contains(r)));
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

  @NotNull
  private static Set<Integer> getOtherNames(@NotNull List<ChangeData> changesList) {
    Set<Integer> otherNames = ContainerUtil.newHashSet();
    for (ChangeData data : changesList) {
      if (data != null && data.otherPath != -1) {
        otherNames.add(data.otherPath);
      }
    }
    return otherNames;
  }

  private static class PathsIndexer implements DataIndexer<Integer, List<ChangeData>, VcsFullCommitDetails> {
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
    public Map<Integer, List<ChangeData>> map(@NotNull VcsFullCommitDetails inputData) {
      Map<Integer, List<ChangeData>> result = new THashMap<>();

      // its not exactly parents count since it is very convenient to assume that initial commit has one parent
      int parentsCount = inputData.getParents().isEmpty() ? 1 : inputData.getParents().size();
      for (int parent = 0; parent < parentsCount; parent++) {
        Collection<Couple<String>> moves;
        Collection<String> changedPaths;
        if (inputData instanceof VcsIndexableDetails) {
          changedPaths = ((VcsIndexableDetails)inputData).getModifiedPaths(parent);
          moves = ((VcsIndexableDetails)inputData).getRenamedPaths(parent);
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

        int finalParent = parent;
        moves.forEach(move -> {
          changedPaths.add(PathUtil.getParentPath(move.first));
          changedPaths.add(PathUtil.getParentPath(move.second));
          // we need to index all parents for the moves
          // so it makes sense to add them there
        });
        getParentPaths(changedPaths, inputData.getRoot()).forEach(changedPath -> {
          try {
            addChangeToResult(result, finalParent, parentsCount, changedPath, null);
          }
          catch (IOException e) {
            myFatalErrorConsumer.consume(e);
          }
        });
        moves.forEach(renamedPaths -> {
          try {
            addChangeToResult(result, finalParent, parentsCount, renamedPaths.second, renamedPaths.first);
          }
          catch (IOException e) {
            myFatalErrorConsumer.consume(e);
          }
        });
      }

      return result;
    }

    private void addChangeToResult(@NotNull Map<Integer, List<ChangeData>> commitChangesMap, int parent,
                                   int parentsCount, @NotNull String afterPath, @Nullable String beforePath) throws IOException {
      int afterId = myPathsEnumerator.enumerate(afterPath);
      List<ChangeData> changeDataList = getOrCreateChangeDataListForPath(commitChangesMap, afterId, parentsCount);
      if (beforePath == null) {
        addChange(changeDataList, parent, new ChangeData(ChangeKind.MODIFIED, -1));
      }
      else {
        int beforeId = myPathsEnumerator.enumerate(beforePath);
        if (beforeId == afterId && !SystemInfo.isFileSystemCaseSensitive) {
          // case only rename in case insensitive file system
          // since ids for before and after paths are the same we just treating this rename as a modification
          addChange(changeDataList, parent, new ChangeData(ChangeKind.MODIFIED, -1));
        }
        else {
          addChange(changeDataList, parent, new ChangeData(ChangeKind.RENAMED_TO, beforeId));
          List<ChangeData> beforeChangeDataList = getOrCreateChangeDataListForPath(commitChangesMap, beforeId, parentsCount);
          addChange(beforeChangeDataList, parent, new ChangeData(ChangeKind.RENAMED_FROM, afterId));
        }
      }
    }

    @NotNull
    private static List<ChangeData> getOrCreateChangeDataListForPath(@NotNull Map<Integer, List<ChangeData>> pathIdToChangeDataListsMap,
                                                                     int pathId, int parentsCount) {
      List<ChangeData> changeDataList = pathIdToChangeDataListsMap.get(pathId);
      if (changeDataList == null) {
        changeDataList = ContainerUtil.newSmartList();
        for (int i = 0; i < parentsCount; i++) {
          changeDataList.add(null);
        }
        pathIdToChangeDataListsMap.put(pathId, changeDataList);
      }
      return changeDataList;
    }

    private static void addChange(@NotNull List<ChangeData> changeDataList, int parentIndex, @NotNull ChangeData change) {
      ChangeData existingChange = changeDataList.get(parentIndex);
      // most of the time, existing change is null
      // but in case insensitive fs it is possible to have several changes for one file
      // example two changes: R: abc -> AAA, D: aaa
      // in this case we keep rename information
      if (existingChange == null || (existingChange.kind != ChangeKind.RENAMED_FROM && existingChange.kind != ChangeKind.RENAMED_TO)) {
        changeDataList.set(parentIndex, change);
      }
    }

    @NotNull
    private static Collection<String> getParentPaths(@NotNull Collection<String> paths, @NotNull VirtualFile root) {
      Set<String> result = ContainerUtil.newHashSet();
      for (String path : paths) {
        while (!path.isEmpty() && !result.contains(path)) {
          result.add(path);
          if (FileUtil.PATH_HASHING_STRATEGY.equals(root.getPath(), path)) break;

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

  private static class ChangeDataListKeyDescriptor implements DataExternalizer<List<ChangeData>> {
    @Override
    public void save(@NotNull DataOutput out, List<ChangeData> value) throws IOException {
      DataInputOutputUtil.writeINT(out, value.size());
      for (ChangeData data : value) {
        if (data == null) {
          out.writeBoolean(false);
        }
        else {
          out.writeBoolean(true);
          out.writeByte(data.kind.id);
          if (data.kind == ChangeKind.RENAMED_TO || data.kind == ChangeKind.RENAMED_FROM) {
            out.writeInt(data.otherPath);
          }
        }
      }
    }

    @Override
    public List<ChangeData> read(@NotNull DataInput in) throws IOException {
      List<ChangeData> value = ContainerUtil.newSmartList();

      int size = DataInputOutputUtil.readINT(in);
      for (int i = 0; i < size; i++) {
        if (in.readBoolean()) {
          ChangeKind kind = ChangeKind.getKind(in.readByte());
          int otherPath;
          if (kind == ChangeKind.RENAMED_TO || kind == ChangeKind.RENAMED_FROM) {
            otherPath = in.readInt();
          }
          else {
            otherPath = -1;
          }
          value.add(new ChangeData(kind, otherPath));
        }
        else {
          value.add(null);
        }
      }

      return value;
    }
  }

  public static class ChangeData {
    @NotNull public final ChangeKind kind;
    public final int otherPath;

    public ChangeData(@NotNull ChangeKind kind, int otherPath) {
      this.kind = kind;
      this.otherPath = otherPath;
    }

    public boolean isRename() {
      return kind.equals(ChangeKind.RENAMED_FROM) || kind.equals(ChangeKind.RENAMED_TO);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ChangeData data = (ChangeData)o;
      return otherPath == data.otherPath &&
             kind == data.kind;
    }

    @Override
    public int hashCode() {
      return Objects.hash(kind, otherPath);
    }
  }

  public enum ChangeKind {
    MODIFIED((byte)0),
    RENAMED_FROM((byte)1),
    RENAMED_TO((byte)2);

    public final byte id;

    ChangeKind(byte id) {
      this.id = id;
    }

    public static ChangeKind getKind(byte id) {
      switch (id) {
        case (0):
          return MODIFIED;
        case (1):
          return RENAMED_FROM;
        case (2):
          return RENAMED_TO;
      }
      throw new IllegalArgumentException("No change kind with id " + id);
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
