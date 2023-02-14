// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.*;
import com.intellij.util.io.storage.AbstractStorage;
import com.intellij.vcs.log.data.VcsLogStorage;
import com.intellij.vcs.log.history.EdgeData;
import com.intellij.vcs.log.impl.VcsLogErrorHandler;
import com.intellij.vcs.log.impl.VcsLogIndexer;
import com.intellij.vcs.log.util.StorageId;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.ObjIntConsumer;

public final class VcsLogPathsIndex extends VcsLogFullDetailsIndex<List<VcsLogPathsIndex.ChangeKind>, VcsLogIndexer.CompressedDetails> {
  private static final Logger LOG = Logger.getInstance(VcsLogPathsIndex.class);
  public static final String PATHS = "paths";
  public static final String INDEX_PATHS_IDS = "paths-ids";
  public static final String RENAMES_MAP = "renames-map";

  private final @NotNull VcsLogPathsIndex.PathIndexer myPathsIndexer;

  public VcsLogPathsIndex(@NotNull StorageId storageId,
                          @NotNull VcsLogStorage storage,
                          @NotNull Set<VirtualFile> roots,
                          @Nullable StorageLockContext storageLockContext,
                          @NotNull VcsLogErrorHandler errorHandler,
                          @NotNull VcsLogStorageBackend store,
                          @NotNull Disposable disposableParent) throws IOException {
    super(storageId,
          PATHS,
          new PathIndexer(storage, createPathsEnumerator(roots, storageId, storageLockContext), store),
          new ChangeKindListKeyDescriptor(),
          storageLockContext,
          errorHandler,
          disposableParent);

    myPathsIndexer = (PathIndexer)myIndexer;
    myPathsIndexer.setFatalErrorConsumer(e -> errorHandler.handleError(VcsLogErrorHandler.Source.Index, e));
  }

  void setMutator(@Nullable VcsLogWriter mutator) {
    myPathsIndexer.mutator = mutator;
  }

  private static @NotNull PersistentEnumerator<LightFilePath> createPathsEnumerator(@NotNull Collection<VirtualFile> roots,
                                                                                    @NotNull StorageId storageId,
                                                                                    @Nullable StorageLockContext storageLockContext) throws IOException {
    Path storageFile = storageId.getStorageFile(INDEX_PATHS_IDS);
    return new PersistentEnumerator<>(storageFile, new LightFilePathKeyDescriptor(roots),
                                      AbstractStorage.PAGE_SIZE, storageLockContext, storageId.getVersion());
  }

  private @Nullable FilePath getPath(int pathId, boolean isDirectory) {
    try {
      return toFilePath(myPathsIndexer.getPathsEnumerator().valueOf(pathId), isDirectory);
    }
    catch (IOException e) {
      myPathsIndexer.myFatalErrorConsumer.consume(e);
    }
    return null;
  }

  @Override
  public void flush() throws StorageException {
    super.flush();
    myPathsIndexer.store.forceRenameMap();
    myPathsIndexer.getPathsEnumerator().force();
  }

  public @Nullable EdgeData<FilePath> findRename(int parent, int child, @NotNull VirtualFile root, @NotNull FilePath path, boolean isChildPath)
    throws IOException {
    int[] renames = myPathsIndexer.store.getRename(parent, child);
    if (renames == null || renames.length == 0) {
      return null;
    }

    int pathId = myPathsIndexer.myPathsEnumerator.enumerate(new LightFilePath(root, path));
    for (int i = 0; i < renames.length; i += 2) {
      int first = renames[i];
      int second = renames[i + 1];
      if ((isChildPath && second == pathId) || (!isChildPath && first == pathId)) {
        FilePath path1 = getPath(first, path.isDirectory());
        FilePath path2 = getPath(second, path.isDirectory());
        return new EdgeData<>(path1, path2);
      }
    }
    return null;
  }

  public void iterateCommits(@NotNull VirtualFile root, @NotNull FilePath path,
                             @NotNull ObjIntConsumer<? super List<ChangeKind>> consumer)
    throws IOException, StorageException {
    int pathId = myPathsIndexer.myPathsEnumerator.enumerate(new LightFilePath(root, path));
    iterateCommitIdsAndValues(pathId, consumer);
  }

  @NotNull
  VcsLogIndexer.PathsEncoder getPathsEncoder() {
    return new VcsLogIndexer.PathsEncoder() {
      @Override
      public int encode(@NotNull VirtualFile root, @NotNull String relativePath, boolean isDirectory) {
        try {
          return myPathsIndexer.myPathsEnumerator.enumerate(new LightFilePath(root, relativePath));
        }
        catch (IOException e) {
          myPathsIndexer.myFatalErrorConsumer.consume(e);
          return 0;
        }
      }
    };
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

  @Contract("null,_ -> null; !null,_ -> !null")
  private static @Nullable FilePath toFilePath(@Nullable LightFilePath lightFilePath, boolean isDirectory) {
    if (lightFilePath == null) return null;
    return VcsUtil.getFilePath(lightFilePath.getRoot().getPath() + "/" + lightFilePath.getRelativePath(), isDirectory);
  }

  private static final class PathIndexer implements DataIndexer<Integer, List<ChangeKind>, VcsLogIndexer.CompressedDetails> {
    private final @NotNull VcsLogStorage myStorage;
    private final @NotNull PersistentEnumerator<LightFilePath> myPathsEnumerator;
    private final VcsLogStorageBackend store;
    @Nullable VcsLogWriter mutator;
    private @NotNull Consumer<? super Exception> myFatalErrorConsumer = LOG::error;

    private PathIndexer(@NotNull VcsLogStorage storage,
                        @NotNull PersistentEnumerator<LightFilePath> enumerator,
                        @NotNull VcsLogStorageBackend store) {
      myStorage = storage;
      myPathsEnumerator = enumerator;
      this.store = store;
    }

    public void setFatalErrorConsumer(@NotNull Consumer<? super Exception> fatalErrorConsumer) {
      myFatalErrorConsumer = fatalErrorConsumer;
    }

    @Override
    public @NotNull Map<Integer, List<ChangeKind>> map(@NotNull VcsLogIndexer.CompressedDetails inputData) {
      Int2ObjectMap<List<ChangeKind>> result = new Int2ObjectOpenHashMap<>();

      // it's not exactly parents count since it is very convenient to assume that initial commit has one parent
      int parentsCount = inputData.getParents().isEmpty() ? 1 : inputData.getParents().size();
      for (int parentIndex = 0; parentIndex < parentsCount; parentIndex++) {
        try {
          ObjectSet<Int2IntMap.Entry> entries = inputData.getRenamedPaths(parentIndex).int2IntEntrySet();
          if (!entries.isEmpty()) {
            int[] renames = new int[entries.size() * 2];
            int index = 0;
            for (Int2IntMap.Entry entry : entries) {
              renames[index++] = entry.getIntKey();
              renames[index++] = entry.getIntValue();
              getOrCreateChangeKindList(result, entry.getIntKey(), parentsCount).set(parentIndex, ChangeKind.REMOVED);
              getOrCreateChangeKindList(result, entry.getIntValue(), parentsCount).set(parentIndex, ChangeKind.ADDED);
            }

            int commit = myStorage.getCommitIndex(inputData.getId(), inputData.getRoot());
            int parent = myStorage.getCommitIndex(inputData.getParents().get(parentIndex), inputData.getRoot());
            if (mutator == null) {
              store.putRename(parent, commit, renames);
            }
            else {
              mutator.putRename(parent, commit, renames);
            }
          }

          for (Int2ObjectMap.Entry<Change.Type> entry : inputData.getModifiedPaths(parentIndex).int2ObjectEntrySet()) {
            getOrCreateChangeKindList(result, entry.getIntKey(), parentsCount).set(parentIndex, createChangeData(entry.getValue()));
          }
        }
        catch (IOException e) {
          myFatalErrorConsumer.consume(e);
        }
      }

      return result;
    }

    private static @NotNull List<ChangeKind> getOrCreateChangeKindList(@NotNull Int2ObjectMap<List<ChangeKind>> pathIdToChangeDataListsMap,
                                                                       int pathId,
                                                                       int parentsCount) {
      List<ChangeKind> changeDataList = pathIdToChangeDataListsMap.get(pathId);
      if (changeDataList == null) {
        if (parentsCount == 1) {
          changeDataList = new SmartList<>(ChangeKind.NOT_CHANGED);
        }
        else {
          changeDataList = new ArrayList<>(parentsCount);
          for (int i = 0; i < parentsCount; i++) {
            changeDataList.add(ChangeKind.NOT_CHANGED);
          }
        }
        pathIdToChangeDataListsMap.put(pathId, changeDataList);
      }
      return changeDataList;
    }

    private static @NotNull ChangeKind createChangeData(@NotNull Change.Type type) {
      return switch (type) {
        case NEW -> ChangeKind.ADDED;
        case DELETED -> ChangeKind.REMOVED;
        case MOVED, MODIFICATION -> ChangeKind.MODIFIED;
      };
    }

    public @NotNull PersistentEnumerator<LightFilePath> getPathsEnumerator() {
      return myPathsEnumerator;
    }
  }

  private static class ChangeKindListKeyDescriptor implements DataExternalizer<List<ChangeKind>> {
    @Override
    public void save(@NotNull DataOutput out, List<ChangeKind> value) throws IOException {
      DataInputOutputUtil.writeINT(out, value.size());
      for (ChangeKind data : value) {
        out.writeByte(data.id);
      }
    }

    @Override
    public List<ChangeKind> read(@NotNull DataInput in) throws IOException {
      List<ChangeKind> value = new SmartList<>();

      int size = DataInputOutputUtil.readINT(in);
      for (int i = 0; i < size; i++) {
        value.add(ChangeKind.getChangeKindById(in.readByte()));
      }

      return value;
    }
  }

  public enum ChangeKind {
    MODIFIED((byte)0),
    NOT_CHANGED((byte)1), // we do not want to have nulls in lists
    ADDED((byte)2),
    REMOVED((byte)3);

    public final byte id;

    ChangeKind(byte id) {
      this.id = id;
    }

    private static final ChangeKind[] KINDS;

    static {
      KINDS = new ChangeKind[4];
      for (ChangeKind kind : values()) {
        KINDS[kind.id] = kind;
      }
    }

    public static @NotNull ChangeKind getChangeKindById(byte id) throws IOException {
      ChangeKind kind = id >= 0 && id < KINDS.length ? KINDS[id] : null;
      if (kind == null) throw new IOException("Change kind by id " + id + " not found.");
      return kind;
    }
  }

  private static final class LightFilePath {
    private final @NotNull VirtualFile myRoot;
    private final @NotNull String myRelativePath;

    private LightFilePath(@NotNull VirtualFile root, @NotNull String relativePath) {
      myRoot = root;
      myRelativePath = relativePath;
    }

    private LightFilePath(@NotNull VirtualFile root, @NotNull FilePath filePath) {
      this(root, VcsFileUtil.relativePath(root, filePath));
    }

    public @NotNull VirtualFile getRoot() {
      return myRoot;
    }

    public @NotNull String getRelativePath() {
      return myRelativePath;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      LightFilePath path = (LightFilePath)o;
      return myRoot.equals(path.myRoot) &&
             myRelativePath.equals(path.myRelativePath);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myRoot, myRelativePath);
    }
  }

  private static final class LightFilePathKeyDescriptor implements KeyDescriptor<LightFilePath> {
    private final @NotNull List<VirtualFile> myRoots;
    private final @NotNull Object2IntMap<VirtualFile> myRootsReversed;

    private LightFilePathKeyDescriptor(@NotNull Collection<VirtualFile> roots) {
      myRoots = ContainerUtil.sorted(roots, Comparator.comparing(VirtualFile::getPath));

      myRootsReversed = new Object2IntOpenHashMap<>();
      for (int i = 0; i < myRoots.size(); i++) {
        myRootsReversed.put(myRoots.get(i), i);
      }
    }

    @Override
    public int getHashCode(@NotNull LightFilePath path) {
      return path.hashCode();
    }

    @Override
    public boolean isEqual(@Nullable LightFilePath path1, @Nullable LightFilePath path2) {
      return Objects.equals(path1, path2);
    }

    @Override
    public void save(@NotNull DataOutput out, LightFilePath value) throws IOException {
      out.writeInt(myRootsReversed.getInt(value.getRoot()));
      IOUtil.writeUTF(out, value.getRelativePath());
    }

    @Override
    public LightFilePath read(@NotNull DataInput in) throws IOException {
      int rootIndex = in.readInt();
      VirtualFile root = myRoots.get(rootIndex);
      if (root == null) throw new IOException("Can not read root for index " + rootIndex + ". All roots " + myRoots);
      String path = IOUtil.readUTF(in);
      return new LightFilePath(root, path);
    }
  }
}
