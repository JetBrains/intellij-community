// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator.DurableEnumeratorFactory;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.io.*;
import com.intellij.util.io.dev.enumerator.KeyDescriptorEx;
import com.intellij.util.io.storage.AbstractStorage;
import com.intellij.vcs.log.data.VcsLogStorage;
import com.intellij.vcs.log.impl.VcsLogErrorHandler;
import com.intellij.vcs.log.impl.VcsLogIndexer.CompressedDetails;
import com.intellij.vcs.log.util.StorageId;
import com.intellij.vcsUtil.VcsUtil;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;

final class VcsLogPathsIndex extends VcsLogFullDetailsIndex<List<ChangeKind>, CompressedDetails> {
  private static final Logger LOG = Logger.getInstance(VcsLogPathsIndex.class);
  public static final String PATHS = "paths";
  public static final String INDEX_PATHS_IDS = "paths-ids";
  public static final String RENAMES_MAP = "renames-map";

  private final @NotNull VcsLogPathsIndex.PathIndexer myPathsIndexer;

  VcsLogPathsIndex(@NotNull StorageId.Directory storageId, @Nullable StorageLockContext storageLockContext,
                   @NotNull PathIndexer pathIndexer, @NotNull VcsLogErrorHandler errorHandler,
                   @NotNull Disposable disposableParent) throws IOException {
    super(createMapReduceIndex(PATHS, storageId, pathIndexer, new ChangeKindListKeyDescriptor(), storageLockContext,
                               null, null, errorHandler), disposableParent);
    myPathsIndexer = pathIndexer;
  }

  private static @NotNull DurableDataEnumerator<LightFilePath> createPathsEnumerator(@NotNull Collection<VirtualFile> roots,
                                                                                     @NotNull StorageId.Directory storageId,
                                                                                     @Nullable StorageLockContext storageLockContext,
                                                                                     boolean useDurableEnumerator)
    throws IOException {
    Path storageFile = storageId.getStorageFile(INDEX_PATHS_IDS);
    if (useDurableEnumerator) {
      return DurableEnumeratorFactory.defaultWithDurableMap(new LightFilePathKeyDescriptorEx(roots)).open(storageFile);
    }
    return new PersistentEnumerator<>(storageFile, new LightFilePathKeyDescriptor(roots),
                                      AbstractStorage.PAGE_SIZE, storageLockContext, storageId.getVersion());
  }

  @Nullable
  FilePath getPath(int pathId, boolean isDirectory) {
    try {
      return toFilePath(getPath(pathId), isDirectory);
    }
    catch (IOException e) {
      myPathsIndexer.myFatalErrorConsumer.accept(e);
    }
    return null;
  }

  int getPathId(@NotNull LightFilePath path) throws IOException {
    return myPathsIndexer.myPathsEnumerator.enumerate(path);
  }

  @Nullable
  LightFilePath getPath(int pathId) throws IOException {
    return myPathsIndexer.myPathsEnumerator.valueOf(pathId);
  }

  @Override
  public void flush() throws StorageException, IOException {
    super.flush();
    myPathsIndexer.myRenamesMap.force();
    myPathsIndexer.myPathsEnumerator.force();
  }

  @Contract("null,_ -> null; !null,_ -> !null")
  static @Nullable FilePath toFilePath(@Nullable LightFilePath lightFilePath, boolean isDirectory) {
    if (lightFilePath == null) return null;
    return VcsUtil.getFilePath(lightFilePath.getRoot().getPath() + "/" + lightFilePath.getRelativePath(), isDirectory);
  }

  static @NotNull VcsLogPathsIndex create(@NotNull StorageId.Directory storageId, @Nullable StorageLockContext storageLockContext,
                                          @NotNull VcsLogStorage storage, @NotNull Set<VirtualFile> roots,
                                          @NotNull PersistentHashMap<int[], int[]> renamesMap,
                                          boolean useDurableEnumerator, @NotNull VcsLogErrorHandler errorHandler,
                                          @NotNull Disposable disposableParent) throws IOException {
    Disposable disposable = Disposer.newDisposable(disposableParent);
    try {
      DurableDataEnumerator<LightFilePath> pathsEnumerator = createPathsEnumerator(roots, storageId, storageLockContext,
                                                                                   useDurableEnumerator);
      Disposer.register(disposable, () -> catchAndWarn(LOG, pathsEnumerator::close));

      PathIndexer pathsIndex = new PathIndexer(storage, pathsEnumerator, renamesMap,
                                               e -> errorHandler.handleError(VcsLogErrorHandler.Source.Index, e));
      return new VcsLogPathsIndex(storageId, storageLockContext, pathsIndex, errorHandler, disposable);
    }
    catch (Throwable t) {
      Disposer.dispose(disposable);
      throw t;
    }
  }

  static final class PathIndexer implements DataIndexer<Integer, List<ChangeKind>, CompressedDetails> {
    private final @NotNull VcsLogStorage myStorage;
    private final @NotNull DurableDataEnumerator<LightFilePath> myPathsEnumerator;
    private final @NotNull PersistentHashMap<int[], int[]> myRenamesMap;
    private final @NotNull Consumer<? super Exception> myFatalErrorConsumer;

    private PathIndexer(@NotNull VcsLogStorage storage,
                        @NotNull DurableDataEnumerator<LightFilePath> pathsEnumerator,
                        @NotNull PersistentHashMap<int[], int[]> renamesMap,
                        @NotNull Consumer<? super Exception> fatalErrorConsumer) {
      myStorage = storage;
      myPathsEnumerator = pathsEnumerator;
      myRenamesMap = renamesMap;
      myFatalErrorConsumer = fatalErrorConsumer;
    }

    @Override
    public @NotNull Map<Integer, List<ChangeKind>> map(@NotNull CompressedDetails inputData) {
      HashMap<Integer, List<ChangeKind>> result = new HashMap<>();

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
            myRenamesMap.put(new int[]{parent, commit}, renames);
          }

          for (Int2ObjectMap.Entry<Change.Type> entry : inputData.getModifiedPaths(parentIndex).int2ObjectEntrySet()) {
            getOrCreateChangeKindList(result, entry.getIntKey(), parentsCount).set(parentIndex, createChangeData(entry.getValue()));
          }
        }
        catch (IOException e) {
          myFatalErrorConsumer.accept(e);
        }
      }

      return result;
    }

    static @NotNull List<ChangeKind> getOrCreateChangeKindList(@NotNull Map<Integer, List<ChangeKind>> pathIdToChangeDataListsMap,
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

    static @NotNull ChangeKind createChangeData(@NotNull Change.Type type) {
      return switch (type) {
        case NEW -> ChangeKind.ADDED;
        case DELETED -> ChangeKind.REMOVED;
        case MOVED, MODIFICATION -> ChangeKind.MODIFIED;
      };
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

  private static class LightFilePathEqualityPolicy implements EqualityPolicy<LightFilePath> {
    protected final @NotNull List<VirtualFile> myRoots;
    protected final @NotNull Object2IntMap<VirtualFile> myRootsReversed;

    private LightFilePathEqualityPolicy(@NotNull Collection<VirtualFile> roots) {
      myRoots = ContainerUtil.sorted(roots, Comparator.comparing(VirtualFile::getPath));

      myRootsReversed = new Object2IntOpenHashMap<>();
      for (int i = 0; i < myRoots.size(); i++) {
        myRootsReversed.put(myRoots.get(i), i);
      }
    }

    @Override
    public int getHashCode(@NotNull LightFilePath path) {
      return 31 * myRootsReversed.getInt(path.getRoot()) + path.getRelativePath().hashCode();
    }

    @Override
    public boolean isEqual(@Nullable LightFilePath path1, @Nullable LightFilePath path2) {
      if (path1 == null || path2 == null) {
        return path1 == path2;
      }
      return myRootsReversed.getInt(path1.getRoot()) == myRootsReversed.getInt(path2.getRoot()) &&
             path1.getRelativePath().equals(path2.getRelativePath());
    }
  }

  private static final class LightFilePathKeyDescriptor extends LightFilePathEqualityPolicy implements KeyDescriptor<LightFilePath> {
    private LightFilePathKeyDescriptor(@NotNull Collection<VirtualFile> roots) {
      super(roots);
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

  private static final class LightFilePathKeyDescriptorEx extends LightFilePathEqualityPolicy implements KeyDescriptorEx<LightFilePath> {
    private LightFilePathKeyDescriptorEx(@NotNull Collection<VirtualFile> roots) {
      super(roots);
    }

    @Override
    public LightFilePath read(@NotNull ByteBuffer input) throws IOException {
      int rootIndex = input.getInt();
      VirtualFile root = myRoots.get(rootIndex);
      if (root == null) throw new IOException("Can not read root for index " + rootIndex + ". All roots " + myRoots);

      byte[] dst = new byte[input.remaining()];
      input.get(dst);
      var path = new String(dst, UTF_8);

      return new LightFilePath(root, path);
    }

    @Override
    public KnownSizeRecordWriter writerFor(@NotNull LightFilePath key) throws IOException {
      VirtualFile root = key.getRoot();
      if (!myRootsReversed.containsKey(root)) {
        throw new IOException("Unknown root " + root.getPath() + " for path " + key.getRelativePath() + ". All roots " + myRoots);
      }

      byte[] relativePathBytes = key.getRelativePath().getBytes(UTF_8);
      int rootIndex = myRootsReversed.getInt(root);
      return new KnownSizeRecordWriter() {
        @Override
        public ByteBuffer write(@NotNull ByteBuffer data) {
          return data.putInt(rootIndex)
            .put(relativePathBytes);
        }

        @Override
        public int recordSize() {
          return relativePathBytes.length + Integer.BYTES;
        }
      };
    }
  }
}
