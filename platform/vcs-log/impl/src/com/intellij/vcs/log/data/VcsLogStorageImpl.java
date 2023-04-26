// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.io.*;
import com.intellij.util.io.storage.AbstractStorage;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.index.MyCommitIdKeyDescriptor;
import com.intellij.vcs.log.impl.VcsLogErrorHandler;
import com.intellij.vcs.log.impl.VcsRefImpl;
import com.intellij.vcs.log.util.PersistentUtil;
import com.intellij.vcs.log.util.StorageId;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Supports the int <-> Hash and int <-> VcsRef persistent mappings.
 */
public final class VcsLogStorageImpl implements Disposable, VcsLogStorage {
  private static final @NotNull Logger LOG = Logger.getInstance(VcsLogStorage.class);
  private static final @NotNull @NonNls String HASHES_STORAGE = "hashes";
  private static final @NotNull @NonNls String REFS_STORAGE = "refs";
  private static final @NotNull @NonNls String STORAGE = "storage";
  public static final @NotNull VcsLogStorage EMPTY = new EmptyLogStorage();

  public static final int VERSION = 8;
  public static final int NO_INDEX = -1;
  private static final int REFS_VERSION = 2;

  private final @NotNull StorageId.Directory myHashesStorageId;
  private final @NotNull StorageId.Directory myRefsStorageId;

  private final @NotNull MyPersistentBTreeEnumerator myCommitIdEnumerator;
  private final @NotNull PersistentEnumerator<VcsRef> myRefsEnumerator;
  private final @NotNull VcsLogErrorHandler myErrorHandler;
  private volatile boolean myDisposed = false;

  public VcsLogStorageImpl(@NotNull Project project,
                           @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                           @NotNull VcsLogErrorHandler errorHandler,
                           @NotNull Disposable parent) throws IOException {
    myErrorHandler = errorHandler;

    String logId = PersistentUtil.calcLogId(project, logProviders);

    List<VirtualFile> roots = logProviders.keySet().stream().sorted(Comparator.comparing(VirtualFile::getPath)).toList();
    MyCommitIdKeyDescriptor commitIdKeyDescriptor = new MyCommitIdKeyDescriptor(roots);
    myHashesStorageId = new StorageId.Directory(project.getName(), HASHES_STORAGE, logId, VERSION);
    StorageLockContext storageLockContext = new StorageLockContext();

    myCommitIdEnumerator = IOUtil.openCleanOrResetBroken(() -> new MyPersistentBTreeEnumerator(myHashesStorageId, commitIdKeyDescriptor,
                                                                                               storageLockContext),
                                                         myHashesStorageId.getStorageFile(STORAGE));

    VcsRefKeyDescriptor refsKeyDescriptor = new VcsRefKeyDescriptor(logProviders, commitIdKeyDescriptor);
    myRefsStorageId = new StorageId.Directory(project.getName(), REFS_STORAGE, logId, VERSION + REFS_VERSION);
    myRefsEnumerator = IOUtil.openCleanOrResetBroken(() -> new PersistentEnumerator<>(myRefsStorageId.getStorageFile(STORAGE),
                                                                                      refsKeyDescriptor, AbstractStorage.PAGE_SIZE,
                                                                                      storageLockContext, myRefsStorageId.getVersion()),
                                                     myRefsStorageId.getStorageFile(STORAGE).toFile());
    Disposer.register(parent, this);
  }

  public static @NotNull Function<Integer, Hash> createHashGetter(@NotNull VcsLogStorage storage) {
    return commitIndex -> {
      CommitId commitId = storage.getCommitId(commitIndex);
      return commitId == null ? null : commitId.getHash();
    };
  }

  private @Nullable CommitId doGetCommitId(int index) throws IOException {
    return myCommitIdEnumerator.valueOf(index);
  }

  private int getOrPut(@NotNull Hash hash, @NotNull VirtualFile root) throws IOException {
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
    myCommitIdEnumerator.force();
    myRefsEnumerator.force();
  }

  public @NotNull StorageId getHashesStorageId() {
    return myHashesStorageId;
  }

  public @NotNull StorageId getRefsStorageId() {
    return myRefsStorageId;
  }

  @Override
  public void dispose() {
    try {
      myDisposed = true;
      myCommitIdEnumerator.close();
      myRefsEnumerator.close();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  private void checkDisposed() {
    if (myDisposed) throw new ProcessCanceledException();
  }

  private static class EmptyLogStorage implements VcsLogStorage {
    @Override
    public int getCommitIndex(@NotNull Hash hash, @NotNull VirtualFile root) {
      return 0;
    }

    @Override
    public @NotNull CommitId getCommitId(int commitIndex) {
      throw new UnsupportedOperationException("Illegal access to empty hash map by index " + commitIndex);
    }

    @Override
    public boolean containsCommit(@NotNull CommitId id) {
      return false;
    }

    @Override
    public void iterateCommits(@NotNull Predicate<? super CommitId> consumer) {
    }

    @Override
    public int getRefIndex(@NotNull VcsRef ref) {
      return 0;
    }

    @Override
    public @Nullable VcsRef getVcsRef(int refIndex) {
      throw new UnsupportedOperationException("Illegal access to empty ref map by index " + refIndex);
    }

    @Override
    public void flush() {
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
}
