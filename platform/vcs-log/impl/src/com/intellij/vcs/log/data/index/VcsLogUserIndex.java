// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.KeyCollectionForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.PersistentMapBasedForwardIndex;
import com.intellij.util.io.IntCollectionDataExternalizer;
import com.intellij.util.io.Page;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.util.io.VoidDataExternalizer;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.VcsUserRegistry;
import com.intellij.vcs.log.data.VcsUserKeyDescriptor;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcs.log.util.StorageId;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public final class VcsLogUserIndex extends VcsLogFullDetailsIndex<Void, VcsShortCommitDetails> {
  private static final Logger LOG = Logger.getInstance(VcsLogUserIndex.class);
  @NonNls private static final String USERS = "users";
  @NonNls private static final String USERS_IDS = "users-ids";
  @NotNull private final UserIndexer myUserIndexer;

  public VcsLogUserIndex(@NotNull StorageId storageId,
                         @NotNull VcsUserRegistry userRegistry,
                         @NotNull FatalErrorHandler consumer,
                         @NotNull Disposable disposableParent) throws IOException {
    super(storageId, USERS, new UserIndexer(createUsersEnumerator(storageId, userRegistry)), VoidDataExternalizer.INSTANCE,
          consumer, disposableParent);
    myUserIndexer = (UserIndexer)myIndexer;
    ((UserIndexer)myIndexer).setFatalErrorConsumer(e -> consumer.consume(this, e));
  }

  @Override
  protected @NotNull Pair<ForwardIndex, ForwardIndexAccessor<Integer, Void>> createdForwardIndex() throws IOException {
    return new Pair<>(new PersistentMapBasedForwardIndex(myStorageId.getStorageFile(myName + ".idx"), false),
                      new KeyCollectionForwardIndexAccessor<>(new IntCollectionDataExternalizer()));
  }

  @NotNull
  private static PersistentEnumerator<VcsUser> createUsersEnumerator(@NotNull StorageId storageId,
                                                                     @NotNull VcsUserRegistry userRegistry) throws IOException {
    Path storageFile = storageId.getStorageFile(USERS_IDS);
    return new PersistentEnumerator<>(storageFile, new VcsUserKeyDescriptor(userRegistry), Page.PAGE_SIZE, null,
                                      storageId.getVersion());
  }

  public IntSet getCommitsForUsers(@NotNull Set<? extends VcsUser> users) throws IOException, StorageException {
    IntSet ids = new IntOpenHashSet();
    for (VcsUser user : users) {
      ids.add(myUserIndexer.getUserId(user));
    }
    return getCommitsWithAnyKey(ids);
  }

  @Nullable
  public VcsUser getAuthorForCommit(int commit) throws IOException {
    Collection<Integer> userIds = getKeysForCommit(commit);
    if (userIds == null || userIds.isEmpty()) return null;
    LOG.assertTrue(userIds.size() == 1);
    return myUserIndexer.getUserById(Objects.requireNonNull(getFirstItem(userIds)));
  }

  public int getUserId(@NotNull VcsUser user) throws IOException {
    return myUserIndexer.getUserId(user);
  }

  @Nullable
  public VcsUser getUserById(int id) throws IOException {
    return myUserIndexer.getUserById(id);
  }

  @Override
  public void flush() throws StorageException {
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
    @NotNull private final PersistentEnumerator<VcsUser> myUserEnumerator;
    @NotNull private Consumer<? super Exception> myFatalErrorConsumer = LOG::error;

    UserIndexer(@NotNull PersistentEnumerator<VcsUser> userEnumerator) {
      myUserEnumerator = userEnumerator;
    }

    @NotNull
    @Override
    public Map<Integer, Void> map(@NotNull VcsShortCommitDetails inputData) {
      Int2ObjectMap<Void> result = new Int2ObjectOpenHashMap<>();
      try {
        result.put(myUserEnumerator.enumerate(inputData.getAuthor()), null);
      }
      catch (IOException e) {
        myFatalErrorConsumer.consume(e);
      }
      return result;
    }

    @Nullable
    public VcsUser getUserById(int id) throws IOException {
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
