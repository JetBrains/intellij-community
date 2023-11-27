// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.KeyCollectionForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.PersistentMapBasedForwardIndex;
import com.intellij.util.io.IntCollectionDataExternalizer;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.VoidDataExternalizer;
import com.intellij.util.io.storage.AbstractStorage;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.VcsUserRegistry;
import com.intellij.vcs.log.data.VcsUserKeyDescriptor;
import com.intellij.vcs.log.impl.VcsLogErrorHandler;
import com.intellij.vcs.log.util.StorageId;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

final class VcsLogUserIndex extends VcsLogFullDetailsIndex<Void, VcsShortCommitDetails> {
  private static final Logger LOG = Logger.getInstance(VcsLogUserIndex.class);
  private static final @NonNls String USERS = "users";
  private static final @NonNls String USERS_IDS = "users-ids";
  private final @NotNull UserIndexer myUserIndexer;

  VcsLogUserIndex(@NotNull StorageId.Directory storageId,
                  @Nullable StorageLockContext storageLockContext,
                  @NotNull VcsUserRegistry userRegistry,
                  @NotNull VcsLogErrorHandler errorHandler,
                  @NotNull Disposable disposableParent) throws IOException {
    super(storageId,
          USERS,
          new UserIndexer(createUserEnumerator(storageId, storageLockContext, userRegistry)),
          VoidDataExternalizer.INSTANCE,
          storageLockContext,
          errorHandler,
          disposableParent);
    myUserIndexer = (UserIndexer)myIndexer;
    ((UserIndexer)myIndexer).setFatalErrorConsumer(e -> errorHandler.handleError(VcsLogErrorHandler.Source.Index, e));
  }

  @Override
  protected @NotNull Pair<ForwardIndex, ForwardIndexAccessor<Integer, Void>> createdForwardIndex(@Nullable StorageLockContext storageLockContext) throws IOException {
    return new Pair<>(new PersistentMapBasedForwardIndex(myStorageId.getStorageFile(myName + ".idx"), true, false, storageLockContext),
                      new KeyCollectionForwardIndexAccessor<>(new IntCollectionDataExternalizer()));
  }

  private static @NotNull PersistentEnumerator<VcsUser> createUserEnumerator(@NotNull StorageId.Directory storageId,
                                                                             @Nullable StorageLockContext storageLockContext,
                                                                             @NotNull VcsUserRegistry userRegistry) throws IOException {
    Path storageFile = storageId.getStorageFile(USERS_IDS);
    return new PersistentEnumerator<>(storageFile, new VcsUserKeyDescriptor(userRegistry), AbstractStorage.PAGE_SIZE, storageLockContext,
                                      storageId.getVersion());
  }

  @NotNull IntSet getCommitsForUsers(@NotNull Set<? extends VcsUser> users) throws IOException, StorageException {
    IntSet ids = new IntOpenHashSet();
    for (VcsUser user : users) {
      ids.add(myUserIndexer.getUserId(user));
    }
    return getCommitsWithAnyKey(ids);
  }

  @Nullable VcsUser getAuthorForCommit(int commitId) {
    try {
      Collection<Integer> userIds = getKeysForCommit(commitId);
      if (userIds == null || userIds.isEmpty()) {
        return null;
      }
      LOG.assertTrue(userIds.size() == 1);
      return myUserIndexer.getUserById(Objects.requireNonNull(getFirstItem(userIds)));
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  int getUserId(@NotNull VcsUser user) {
    try {
      return myUserIndexer.getUserId(user);
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Nullable VcsUser getUserById(int id) {
    try {
      return myUserIndexer.getUserById(id);
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void flush() throws StorageException, IOException {
    super.flush();
    myUserIndexer.flush();
  }

  @Override
  public void dispose() {
    super.dispose();
    try {
      myUserIndexer.close();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  private static final class UserIndexer implements DataIndexer<Integer, Void, VcsShortCommitDetails> {
    private final @NotNull PersistentEnumerator<VcsUser> myUserEnumerator;
    private @NotNull Consumer<? super Exception> myFatalErrorConsumer = LOG::error;

    UserIndexer(@NotNull PersistentEnumerator<VcsUser> userEnumerator) {
      myUserEnumerator = userEnumerator;
    }

    @Override
    public @NotNull Map<Integer, Void> map(@NotNull VcsShortCommitDetails inputData) {
      Int2ObjectMap<Void> result = new Int2ObjectOpenHashMap<>();
      try {
        result.put(myUserEnumerator.enumerate(inputData.getAuthor()), null);
      }
      catch (IOException e) {
        myFatalErrorConsumer.accept(e);
      }
      return result;
    }

    public @Nullable VcsUser getUserById(int id) throws IOException {
      return myUserEnumerator.valueOf(id);
    }

    public int getUserId(@NotNull VcsUser user) throws IOException {
      return myUserEnumerator.enumerate(user);
    }

    public void setFatalErrorConsumer(@NotNull Consumer<? super Exception> fatalErrorConsumer) {
      myFatalErrorConsumer = fatalErrorConsumer;
    }

    public void flush() {
      myUserEnumerator.force();
    }

    public void close() throws IOException {
      myUserEnumerator.close();
    }
  }
}
