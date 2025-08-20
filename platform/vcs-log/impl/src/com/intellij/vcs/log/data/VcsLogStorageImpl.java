// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.CommonProcessors;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.io.*;
import com.intellij.util.io.storage.AbstractStorage;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.index.PhmVcsLogStorageBackend;
import com.intellij.vcs.log.data.index.VcsLogStorageBackend;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.VcsLogErrorHandler;
import com.intellij.vcs.log.impl.VcsRefImpl;
import com.intellij.vcs.log.util.StorageId;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

/**
 * Supports the int <-> Hash and int <-> VcsRef persistent mappings.
 */
@ApiStatus.Internal
public final class VcsLogStorageImpl implements Disposable, VcsLogStorage {
  private static final @NotNull Logger LOG = Logger.getInstance(VcsLogStorage.class);
  private static final @NotNull @NonNls String HASHES_STORAGE = "hashes";
  private static final @NotNull @NonNls String REFS_STORAGE = "refs";
  private static final @NotNull @NonNls String STORAGE = "storage";

  private static final @NotNull String STORAGE_CLOSED_MESSAGE = "Storage is closed";

  public static final int VERSION = 8;
  public static final int NO_INDEX = -1;
  private static final int REFS_VERSION = 2;

  private final @NotNull StorageId.Directory myHashesStorageId;
  private final @NotNull StorageId.Directory myRefsStorageId;

  private @Nullable MyPersistentBTreeEnumerator myCommitIdEnumerator;
  private @Nullable PersistentEnumerator<VcsRef> myRefsEnumerator;
  private final @NotNull VcsLogErrorHandler myErrorHandler;
  private volatile boolean myDisposed = false;

  private VcsLogStorageImpl(@NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                            @NotNull StorageId.Directory hashesStorageId,
                            @NotNull StorageId.Directory refsStorageId,
                            @NotNull VcsLogErrorHandler errorHandler,
                            @NotNull Disposable parent) throws IOException {
    myErrorHandler = errorHandler;

    Disposer.register(parent, this);

    try {
      StorageLockContext storageLockContext = new StorageLockContext();

      List<VirtualFile> roots = logProviders.keySet().stream().sorted(Comparator.comparing(VirtualFile::getPath)).toList();
      MyCommitIdKeyDescriptor commitIdKeyDescriptor = new MyCommitIdKeyDescriptor(roots);
      myHashesStorageId = hashesStorageId;
      MyPersistentBTreeEnumerator commitIdEnumerator =
        new MyPersistentBTreeEnumerator(myHashesStorageId, commitIdKeyDescriptor, storageLockContext);
      myCommitIdEnumerator = commitIdEnumerator;
      Disposer.register(this, () -> {
        try {
          commitIdEnumerator.close();
        }
        catch (IOException e) {
          LOG.warn(e);
        }
      });

      VcsRefKeyDescriptor refsKeyDescriptor = new VcsRefKeyDescriptor(logProviders, commitIdKeyDescriptor);
      myRefsStorageId = refsStorageId;
      PersistentEnumerator<VcsRef> refsEnumerator =
        new PersistentEnumerator<>(myRefsStorageId.getStorageFile(STORAGE), refsKeyDescriptor, AbstractStorage.PAGE_SIZE,
                                   storageLockContext, myRefsStorageId.getVersion());
      myRefsEnumerator = refsEnumerator;
      Disposer.register(this, () -> {
        try {
          refsEnumerator.close();
        }
        catch (IOException e) {
          LOG.warn(e);
        }
      });

      Disposer.register(this, () -> myDisposed = true);
    }
    catch (Throwable t) {
      myDisposed = true;
      Disposer.dispose(this);
      throw t;
    }
  }

  private @Nullable CommitId doGetCommitId(int index) throws IOException {
    if (myCommitIdEnumerator == null) {
      throw new IllegalStateException(STORAGE_CLOSED_MESSAGE);
    }
    return myCommitIdEnumerator.valueOf(index);
  }

  private int getOrPut(@NotNull Hash hash, @NotNull VirtualFile root) throws IOException {
    if (myCommitIdEnumerator == null) {
      throw new IllegalStateException(STORAGE_CLOSED_MESSAGE);
    }
    return myCommitIdEnumerator.enumerate(new CommitId(hash, root));
  }

  @Override
  public int getCommitIndex(@NotNull Hash hash, @NotNull VirtualFile root) {
    checkDisposed();
    try {
      return getOrPut(hash, root);
    }
    catch (IOException e) {
      myErrorHandler.handleError(VcsLogErrorHandler.Source.Storage, e);
    }
    return NO_INDEX;
  }

  @Override
  public @Nullable CommitId getCommitId(int commitIndex) {
    checkDisposed();
    try {
      CommitId commitId = doGetCommitId(commitIndex);
      if (commitId == null) {
        myErrorHandler.handleError(VcsLogErrorHandler.Source.Storage, new RuntimeException("Unknown commit index: " + commitIndex));
      }
      return commitId;
    }
    catch (IOException e) {
      myErrorHandler.handleError(VcsLogErrorHandler.Source.Storage, e);
    }
    return null;
  }

  @Override
  public boolean containsCommit(@NotNull CommitId id) {
    checkDisposed();
    if (myCommitIdEnumerator == null) {
      throw new IllegalStateException(STORAGE_CLOSED_MESSAGE);
    }
    try {
      return myCommitIdEnumerator.contains(id);
    }
    catch (IOException e) {
      myErrorHandler.handleError(VcsLogErrorHandler.Source.Storage, e);
    }
    return false;
  }

  @Override
  public void iterateCommits(@NotNull Predicate<? super CommitId> consumer) {
    checkDisposed();
    if (myCommitIdEnumerator == null) {
      throw new IllegalStateException(STORAGE_CLOSED_MESSAGE);
    }
    try {
      myCommitIdEnumerator.iterateData(new CommonProcessors.FindProcessor<>() {
        @Override
        protected boolean accept(CommitId commitId) {
          return !consumer.test(commitId);
        }
      });
    }
    catch (IOException e) {
      myErrorHandler.handleError(VcsLogErrorHandler.Source.Storage, e);
    }
  }

  @Override
  public int getRefIndex(@NotNull VcsRef ref) {
    checkDisposed();
    if (myRefsEnumerator == null) {
      throw new IllegalStateException(STORAGE_CLOSED_MESSAGE);
    }
    try {
      return myRefsEnumerator.enumerate(ref);
    }
    catch (IOException e) {
      myErrorHandler.handleError(VcsLogErrorHandler.Source.Storage, e);
    }
    return NO_INDEX;
  }

  @Override
  public @Nullable VcsRef getVcsRef(int refIndex) {
    checkDisposed();
    if (myRefsEnumerator == null) {
      throw new IllegalStateException(STORAGE_CLOSED_MESSAGE);
    }
    try {
      return myRefsEnumerator.valueOf(refIndex);
    }
    catch (IOException e) {
      myErrorHandler.handleError(VcsLogErrorHandler.Source.Storage, e);
      return null;
    }
  }

  @Override
  public void flush() {
    checkDisposed();
    if (myCommitIdEnumerator == null) {
      throw new IllegalStateException(STORAGE_CLOSED_MESSAGE);
    }
    myCommitIdEnumerator.force();
    
    if (myRefsEnumerator == null) {
      throw new IllegalStateException(STORAGE_CLOSED_MESSAGE);
    }
    myRefsEnumerator.force();
  }

  public @NotNull StorageId getHashesStorageId() {
    return myHashesStorageId;
  }

  public @NotNull StorageId getRefsStorageId() {
    return myRefsStorageId;
  }

  private void checkDisposed() {
    if (myDisposed) throw new ProcessCanceledException();
  }

  @ApiStatus.Internal
  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public void dispose() {
    // nullize references to ensure that backing files can be closed and deleted
    myCommitIdEnumerator = null;
    myRefsEnumerator = null;
  }

  private static class MyCommitIdKeyDescriptor implements KeyDescriptor<CommitId> {
    private final @NotNull List<? extends VirtualFile> myRoots;
    private final @NotNull Object2IntMap<VirtualFile> myRootsReversed;

    MyCommitIdKeyDescriptor(@NotNull List<? extends VirtualFile> roots) {
      myRoots = roots;

      myRootsReversed = new Object2IntOpenHashMap<>();
      for (int i = 0; i < roots.size(); i++) {
        myRootsReversed.put(roots.get(i), i);
      }
    }

    @Override
    public void save(@NotNull DataOutput out, CommitId value) throws IOException {
      ((HashImpl)value.getHash()).write(out);
      out.writeInt(myRootsReversed.getInt(value.getRoot()));
    }

    @Override
    public CommitId read(@NotNull DataInput in) throws IOException {
      Hash hash = HashImpl.read(in);
      VirtualFile root = myRoots.get(in.readInt());
      if (root == null) return null;
      return new CommitId(hash, root);
    }

    @Override
    public int getHashCode(CommitId value) {
      int result = value.getHash().hashCode();
      result = 31 * result + myRootsReversed.getInt(value);
      return result;
    }

    @Override
    public boolean isEqual(@Nullable CommitId val1, @Nullable CommitId val2) {
      if (val1 == val2) return true;
      if (val1 == null || val2 == null) return false;
      return val1.getHash().equals(val2.getHash()) &&
             myRootsReversed.getInt(val1.getRoot()) == myRootsReversed.getInt(val2.getRoot());
    }
  }

  private static class VcsRefKeyDescriptor implements KeyDescriptor<VcsRef> {
    private final @NotNull Map<VirtualFile, VcsLogProvider> myLogProviders;
    private final @NotNull KeyDescriptor<CommitId> myCommitIdKeyDescriptor;

    VcsRefKeyDescriptor(@NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                        @NotNull KeyDescriptor<CommitId> commitIdKeyDescriptor) {
      myLogProviders = logProviders;
      myCommitIdKeyDescriptor = commitIdKeyDescriptor;
    }

    @Override
    public int getHashCode(@NotNull VcsRef value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(@Nullable VcsRef val1, @Nullable VcsRef val2) {
      return Objects.equals(val1, val2);
    }

    @Override
    public void save(@NotNull DataOutput out, @NotNull VcsRef value) throws IOException {
      myCommitIdKeyDescriptor.save(out, new CommitId(value.getCommitHash(), value.getRoot()));
      IOUtil.writeUTF(out, value.getName());
      myLogProviders.get(value.getRoot()).getReferenceManager().serialize(out, value.getType());
    }

    @Override
    public VcsRef read(@NotNull DataInput in) throws IOException {
      CommitId commitId = myCommitIdKeyDescriptor.read(in);
      if (commitId == null) throw new IOException("Can not read commit id for reference");
      String name = IOUtil.readUTF(in);
      VcsRefType type = myLogProviders.get(commitId.getRoot()).getReferenceManager().deserialize(in);
      return new VcsRefImpl(commitId.getHash(), name, type, commitId.getRoot());
    }
  }

  private static final class MyPersistentBTreeEnumerator extends PersistentBTreeEnumerator<CommitId> {
    MyPersistentBTreeEnumerator(@NotNull StorageId.Directory storageId, @NotNull KeyDescriptor<CommitId> commitIdKeyDescriptor,
                                @Nullable StorageLockContext storageLockContext) throws IOException {
      super(storageId.getStorageFile(STORAGE), commitIdKeyDescriptor, AbstractStorage.PAGE_SIZE, storageLockContext,
            storageId.getVersion());
    }

    public boolean contains(@NotNull CommitId id) throws IOException {
      return tryEnumerate(id) != NULL_ID;
    }
  }

  public static @NotNull Pair<VcsLogStorage, @Nullable VcsLogStorageBackend> createStorageAndIndexBackend(@NotNull Project project,
                                                                                                          @NotNull String logId,
                                                                                                          @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                                                                                                          @NotNull Set<VirtualFile> indexingRoots,
                                                                                                          @NotNull VcsLogErrorHandler errorHandler,
                                                                                                          @NotNull Disposable parent)
    throws IOException {
    StorageId.Directory hashesStorageId = new StorageId.Directory(project.getName(), HASHES_STORAGE, logId, VERSION);
    StorageId.Directory refsStorageId = new StorageId.Directory(project.getName(), REFS_STORAGE, logId, VERSION + REFS_VERSION);
    StorageId.Directory indexStorageId = PhmVcsLogStorageBackend.getIndexStorageId(project, logId);

    List<StorageId.Directory> storageIds = List.of(hashesStorageId, refsStorageId, indexStorageId);

    return IOUtil.openCleanOrResetBroken(() -> {
      VcsLogStorageImpl storage = new VcsLogStorageImpl(logProviders, hashesStorageId, refsStorageId, errorHandler, parent);
      if (indexingRoots.isEmpty()) return new Pair<>(storage, null);

      try {
        VcsLogStorageBackend indexBackend = PhmVcsLogStorageBackend.create(project, storage, indexStorageId, indexingRoots, errorHandler, parent);
        return new Pair<>(storage, indexBackend);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error("Could not create index storage backend", e);
        return new Pair<>(storage, null);
      }
    }, () -> cleanupStorageFiles(storageIds));
  }

  @RequiresBackgroundThread
  static void cleanupStorageFiles(@NotNull Collection<? extends StorageId> storageIds) {
    for (StorageId storageId : storageIds) {
      try {
        boolean deleted = storageId.cleanupAllStorageFiles();
        if (deleted) {
          LOG.info("Deleted storage files in " + storageId.getStoragePath());
        }
        else {
          LOG.error("Could not clean up storage files in " + storageId.getStoragePath());
        }
      }
      catch (Exception e) {
        LOG.error("Could not clean up storage files in " + storageId.getStoragePath(), e);
      }
    }
  }
}
