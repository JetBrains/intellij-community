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
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.ForwardIndex;
import com.intellij.util.indexing.impl.KeyCollectionBasedForwardIndex;
import com.intellij.util.io.*;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.VcsUserRegistry;
import com.intellij.vcs.log.data.VcsUserKeyDescriptor;
import com.intellij.vcs.log.impl.FatalErrorHandler;
import com.intellij.vcs.log.util.StorageId;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public class VcsLogUserIndex extends VcsLogFullDetailsIndex<Void, VcsFullCommitDetails> {
  private static final Logger LOG = Logger.getInstance(VcsLogUserIndex.class);
  public static final String USERS = "users";
  public static final String USERS_IDS = "users-ids";
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

  @NotNull
  @Override
  protected ForwardIndex<Integer, Void> createForwardIndex(@NotNull IndexExtension<Integer, Void, VcsFullCommitDetails> extension)
    throws IOException {
    return new KeyCollectionBasedForwardIndex<Integer, Void>(extension) {
      @NotNull
      @Override
      public PersistentHashMap<Integer, Collection<Integer>> createMap() throws IOException {
        File storageFile = myStorageId.getStorageFile(myName + ".idx");
        return new PersistentHashMap<>(storageFile, new IntInlineKeyDescriptor(), new IntCollectionDataExternalizer(), Page.PAGE_SIZE);
      }
    };
  }

  @NotNull
  private static PersistentEnumeratorBase<VcsUser> createUsersEnumerator(@NotNull StorageId storageId,
                                                                         @NotNull VcsUserRegistry userRegistry) throws IOException {
    File storageFile = storageId.getStorageFile(USERS_IDS);
    return new PersistentBTreeEnumerator<>(storageFile, new VcsUserKeyDescriptor(userRegistry), Page.PAGE_SIZE, null,
                                           storageId.getVersion());
  }

  public TIntHashSet getCommitsForUsers(@NotNull Set<? extends VcsUser> users) throws IOException, StorageException {
    Set<Integer> ids = ContainerUtil.newHashSet();
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
    return myUserIndexer.getUserById(notNull(getFirstItem(userIds)));
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

  private static class UserIndexer implements DataIndexer<Integer, Void, VcsFullCommitDetails> {
    @NotNull private final PersistentEnumeratorBase<VcsUser> myUserEnumerator;
    @NotNull private Consumer<? super Exception> myFatalErrorConsumer = LOG::error;

    UserIndexer(@NotNull PersistentEnumeratorBase<VcsUser> userEnumerator) {
      myUserEnumerator = userEnumerator;
    }

    @NotNull
    @Override
    public Map<Integer, Void> map(@NotNull VcsFullCommitDetails inputData) {
      Map<Integer, Void> result = new THashMap<>();

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
