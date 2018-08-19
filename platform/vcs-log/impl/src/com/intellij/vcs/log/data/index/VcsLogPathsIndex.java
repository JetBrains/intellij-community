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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.ForwardIndex;
import com.intellij.util.io.*;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogIndexService;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcs.log.impl.VcsIndexableDetails;
import com.intellij.vcs.log.util.StorageId;
import com.intellij.vcsUtil.VcsUtil;
import gnu.trove.TByteObjectHashMap;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.ObjIntConsumer;

import static com.intellij.util.containers.ContainerUtil.newTroveSet;

public class VcsLogPathsIndex extends VcsLogFullDetailsIndex<List<VcsLogPathsIndex.ChangeData>, VcsIndexableDetails> {
  private static final Logger LOG = Logger.getInstance(VcsLogPathsIndex.class);
  public static final String PATHS = "paths";
  public static final String INDEX_PATHS_IDS = "paths-ids";

  @NotNull private final PathsIndexer myPathsIndexer;

  public VcsLogPathsIndex(@NotNull StorageId storageId,
                          @NotNull Set<VirtualFile> roots,
                          @NotNull FatalErrorHandler fatalErrorHandler,
                          @NotNull Disposable disposableParent) throws IOException {
    super(storageId, PATHS, new PathsIndexer(createPathsEnumerator(storageId), roots),
          new ChangeDataListKeyDescriptor(), fatalErrorHandler, disposableParent);

    myPathsIndexer = (PathsIndexer)myIndexer;
    myPathsIndexer.setFatalErrorConsumer(e -> fatalErrorHandler.consume(this, e));
  }

  @NotNull
  @Override
  protected ForwardIndex<Integer, List<VcsLogPathsIndex.ChangeData>> createForwardIndex(@NotNull IndexExtension<Integer, List<ChangeData>, VcsIndexableDetails> extension)
    throws IOException {
    if (!VcsLogIndexService.isPathsForwardIndexRequired()) return super.createForwardIndex(extension);
    return new VcsLogPathsForwardIndex(extension) {
      @NotNull
      @Override
      public PersistentHashMap<Integer, List<Collection<Integer>>> createMap() throws IOException {
        File storageFile = myStorageId.getStorageFile(myName + ".idx");
        return new PersistentHashMap<>(storageFile, new IntInlineKeyDescriptor(), new IntCollectionListExternalizer(), Page.PAGE_SIZE);
      }
    };
  }

  @NotNull
  private static PersistentEnumeratorBase<LightFilePath> createPathsEnumerator(@NotNull StorageId storageId) throws IOException {
    File storageFile = storageId.getStorageFile(INDEX_PATHS_IDS);
    return new PersistentBTreeEnumerator<>(storageFile, new LightFilePathKeyDescriptor(),
                                           Page.PAGE_SIZE, null, storageId.getVersion());
  }

  @Nullable
  public FilePath getPath(int pathId) {
    try {
      return toFilePath(myPathsIndexer.getPathsEnumerator().valueOf(pathId));
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
      Set<Integer> newRenames = ContainerUtil.newHashSet();
      for (Integer key : renames) {
        iterateCommitIdsAndValues(key, (value, commit) -> {
          result.add(commit);
          newRenames.addAll(ContainerUtil.filter(getOtherNames(value), r -> !allPathIds.contains(r)));
        });
      }
      renames = newRenames;
      allPathIds.addAll(renames);
    }

    return result;
  }

  @NotNull
  public Set<FilePath> getPathsChangedInCommit(int commit, int parentIndex) throws IOException {
    List<Collection<Integer>> keysForCommit = getKeysForCommit(commit);
    if (keysForCommit == null || keysForCommit.size() <= parentIndex) return Collections.emptySet();

    Set<FilePath> paths = ContainerUtil.newHashSet();
    for (Integer pathId : ContainerUtil.newHashSet(keysForCommit.get(parentIndex))) {
      LightFilePath lightFilePath = myPathsIndexer.getPathsEnumerator().valueOf(pathId);
      if (lightFilePath.isDirectory()) continue;
      paths.add(toFilePath(lightFilePath));
    }

    return paths;
  }

  @NotNull
  private Set<Integer> getPathIds(@NotNull Collection<FilePath> paths) throws IOException {
    Set<Integer> allPathIds = ContainerUtil.newHashSet();
    for (FilePath path : paths) {
      allPathIds.add(myPathsIndexer.myPathsEnumerator.enumerate(new LightFilePath(path)));
    }
    return allPathIds;
  }

  @NotNull
  public Set<FilePath> getFileNames(@NotNull FilePath path, int commit) throws IOException, StorageException {
    int startId = myPathsIndexer.myPathsEnumerator.enumerate(new LightFilePath(path));

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
      result.add(toFilePath(myPathsIndexer.myPathsEnumerator.valueOf(id)));
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
        FilePath currentPath = toFilePath(myPathsIndexer.myPathsEnumerator.valueOf(currentPathId));
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

  @Contract("null -> null; !null -> !null")
  @Nullable
  private static FilePath toFilePath(@Nullable LightFilePath lightFilePath) {
    if (lightFilePath == null) return null;
    return VcsUtil.getFilePath(lightFilePath.getPath(), lightFilePath.isDirectory());
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

  private static class PathsIndexer implements DataIndexer<Integer, List<ChangeData>, VcsIndexableDetails> {
    @NotNull private final PersistentEnumeratorBase<LightFilePath> myPathsEnumerator;
    @NotNull private final Set<String> myRoots;
    @NotNull private Consumer<Exception> myFatalErrorConsumer = LOG::error;

    private PathsIndexer(@NotNull PersistentEnumeratorBase<LightFilePath> enumerator, @NotNull Set<VirtualFile> roots) {
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
    public Map<Integer, List<ChangeData>> map(@NotNull VcsIndexableDetails inputData) {
      Map<Integer, List<ChangeData>> result = new THashMap<>();

      // its not exactly parents count since it is very convenient to assume that initial commit has one parent
      int parentsCount = inputData.getParents().isEmpty() ? 1 : inputData.getParents().size();
      for (int parent = 0; parent < parentsCount; parent++) {
        Collection<Couple<LightFilePath>> moves = ContainerUtil.newHashSet(ContainerUtil.map(inputData.getRenamedPaths(parent),
                                                                                             rename -> toLightPathCouple(rename.first,
                                                                                                                         rename.second)));
        Collection<LightFilePath> changedPaths = ContainerUtil.newHashSet(toLightPaths(inputData.getModifiedPaths(parent)));

        int finalParent = parent;
        moves.forEach(move -> {
          changedPaths.add(new LightFilePath(PathUtil.getParentPath(move.first.getPath()), true));
          changedPaths.add(new LightFilePath(PathUtil.getParentPath(move.second.getPath()), true));
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

    @NotNull
    protected Couple<LightFilePath> toLightPathCouple(@NotNull String path1, @NotNull String path2) {
      return new Couple<>(new LightFilePath(path1, false), new LightFilePath(path2, false));
    }

    @NotNull
    protected List<LightFilePath> toLightPaths(@NotNull Collection<String> paths) {
      return ContainerUtil.map(paths, path -> new LightFilePath(path, false));
    }

    private void addChangeToResult(@NotNull Map<Integer, List<ChangeData>> commitChangesMap, int parent,
                                   int parentsCount, @NotNull LightFilePath afterPath, @Nullable LightFilePath beforePath)
      throws IOException {
      int afterId = myPathsEnumerator.enumerate(afterPath);
      List<ChangeData> changeDataList = getOrCreateChangeDataListForPath(commitChangesMap, afterId, parentsCount);
      if (beforePath == null) {
        addChange(changeDataList, parent, ChangeData.MODIFIED);
      }
      else {
        int beforeId = myPathsEnumerator.enumerate(beforePath);
        if (beforeId == afterId && !SystemInfo.isFileSystemCaseSensitive) {
          // case only rename in case insensitive file system
          // since ids for before and after paths are the same we just treating this rename as a modification
          addChange(changeDataList, parent, ChangeData.MODIFIED);
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
          changeDataList.add(ChangeData.NOT_CHANGED);
        }
        pathIdToChangeDataListsMap.put(pathId, changeDataList);
      }
      return changeDataList;
    }

    private static void addChange(@NotNull List<ChangeData> changeDataList, int parentIndex, @NotNull ChangeData change) {
      ChangeData existingChange = changeDataList.get(parentIndex);
      // most of the time, existing change is ChangeData.NOT_CHANGED
      // but in case insensitive fs it is possible to have several changes for one file
      // example two changes: R: abc -> AAA, D: aaa
      // in this case we keep rename information
      if (ChangeData.NOT_CHANGED.equals(existingChange) ||
          (existingChange.kind != ChangeKind.RENAMED_FROM && existingChange.kind != ChangeKind.RENAMED_TO)) {
        changeDataList.set(parentIndex, change);
      }
    }

    @NotNull
    private static Collection<LightFilePath> getParentPaths(@NotNull Collection<LightFilePath> paths, @NotNull VirtualFile root) {
      Set<LightFilePath> result = ContainerUtil.newHashSet();
      for (LightFilePath path : paths) {
        while (!path.getPath().isEmpty() && !result.contains(path)) {
          result.add(path);
          if (FileUtil.PATH_HASHING_STRATEGY.equals(root.getPath(), path.getPath())) break;

          path = new LightFilePath(PathUtil.getParentPath(path.getPath()), true);
        }
      }
      return result;
    }

    @NotNull
    public PersistentEnumeratorBase<LightFilePath> getPathsEnumerator() {
      return myPathsEnumerator;
    }
  }

  private static class ChangeDataListKeyDescriptor implements DataExternalizer<List<ChangeData>> {
    @Override
    public void save(@NotNull DataOutput out, List<ChangeData> value) throws IOException {
      DataInputOutputUtil.writeINT(out, value.size());
      for (ChangeData data : value) {
        out.writeByte(data.kind.id);
        if (data.kind == ChangeKind.RENAMED_TO || data.kind == ChangeKind.RENAMED_FROM) {
          out.writeInt(data.otherPath);
        }
      }
    }

    @Override
    public List<ChangeData> read(@NotNull DataInput in) throws IOException {
      List<ChangeData> value = ContainerUtil.newSmartList();

      int size = DataInputOutputUtil.readINT(in);
      for (int i = 0; i < size; i++) {
        ChangeKind kind = ChangeKind.getChangeKindById(in.readByte());
        int otherPath;
        if (kind == ChangeKind.RENAMED_TO || kind == ChangeKind.RENAMED_FROM) {
          otherPath = in.readInt();
        }
        else {
          otherPath = -1;
        }
        value.add(new ChangeData(kind, otherPath));
      }

      return value;
    }
  }

  public static class ChangeData {
    public static final ChangeData NOT_CHANGED = new ChangeData(ChangeKind.NOT_CHANGED, -1);
    public static final ChangeData MODIFIED = new ChangeData(ChangeKind.MODIFIED, -1);

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
    RENAMED_TO((byte)2),
    NOT_CHANGED((byte)3); // we do not want to have nulls in lists

    public final byte id;

    ChangeKind(byte id) {
      this.id = id;
    }

    private static final TByteObjectHashMap<ChangeKind> KINDS = new TByteObjectHashMap<>();

    static {
      for (ChangeKind kind : ChangeKind.values()) {
        KINDS.put(kind.id, kind);
      }
    }

    @NotNull
    public static ChangeKind getChangeKindById(byte id) {
      return KINDS.get(id);
    }
  }

  private static class LightFilePath {
    @NotNull private final String myPath;
    private final boolean myIsDirectory;

    private LightFilePath(@NotNull String path, boolean directory) {
      myPath = path;
      myIsDirectory = directory;
    }

    private LightFilePath(@NotNull FilePath filePath) {
      this(filePath.getPath(), filePath.isDirectory());
    }

    @NotNull
    public String getPath() {
      return myPath;
    }

    public boolean isDirectory() {
      return myIsDirectory;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      LightFilePath path = (LightFilePath)o;
      return myIsDirectory == path.myIsDirectory &&
             FileUtil.PATH_HASHING_STRATEGY.equals(myPath, path.myPath);
    }

    @Override
    public int hashCode() {
      int result = FileUtil.PATH_HASHING_STRATEGY.computeHashCode(myPath);
      result = 31 * result + (myIsDirectory ? 1 : 0);
      return result;
    }
  }

  private static class LightFilePathKeyDescriptor implements KeyDescriptor<LightFilePath> {
    @Override
    public int getHashCode(LightFilePath path) {
      return path.hashCode();
    }

    @Override
    public boolean isEqual(LightFilePath path1, LightFilePath path2) {
      return path1.equals(path2);
    }

    @Override
    public void save(@NotNull DataOutput out, LightFilePath value) throws IOException {
      String path = value.getPath();
      if (!SystemInfo.isFileSystemCaseSensitive) {
        path = path.toLowerCase();
      }
      IOUtil.writeUTF(out, path);
      out.writeBoolean(value.myIsDirectory);
    }

    @Override
    public LightFilePath read(@NotNull DataInput in) throws IOException {
      String path = IOUtil.readUTF(in);
      boolean isDirectory = in.readBoolean();
      return new LightFilePath(path, isDirectory);
    }
  }
}
