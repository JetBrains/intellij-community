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
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.ScalarIndexExtension;
import com.intellij.util.indexing.StorageException;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.data.VcsUserRegistryImpl;
import com.intellij.vcs.log.impl.FatalErrorConsumer;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class VcsLogUserIndex extends VcsLogFullDetailsIndex<Void> {
  private static final Logger LOG = Logger.getInstance(VcsLogUserIndex.class);
  @NotNull private final VcsUserRegistryImpl myUserRegistry;

  public VcsLogUserIndex(@NotNull String logId,
                         @NotNull VcsUserRegistryImpl userRegistry,
                         @NotNull FatalErrorConsumer consumer,
                         @NotNull Disposable disposableParent) throws IOException {
    super(logId, "users", VcsLogPersistentIndex.getVersion(), new UserIndexer(userRegistry), ScalarIndexExtension.VOID_DATA_EXTERNALIZER,
          disposableParent);
    myUserRegistry = userRegistry;
    ((UserIndexer)myIndexer).setFatalErrorConsumer(e -> {
      consumer.consume(this, e);
      markCorrupted();
    });
  }

  public TIntHashSet getCommitsForUsers(@NotNull Set<VcsUser> users) throws IOException, StorageException {
    Set<Integer> ids = ContainerUtil.newHashSet();
    for (VcsUser user : users) {
      ids.add(myUserRegistry.getUserId(user));
    }
    return getCommitsWithAnyKey(ids);
  }

  @NotNull
  public String getUserInfo(int commit) throws IOException {
    Collection<Integer> keys = getKeysForCommit(commit);
    assert keys != null;
    assert keys.size() == 1;
    return ObjectUtils.assertNotNull(myUserRegistry.getUserById(ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(keys)))).toString();
  }

  private static class UserIndexer implements DataIndexer<Integer, Void, VcsFullCommitDetails> {
    @NotNull private final VcsUserRegistryImpl myRegistry;
    @NotNull private Consumer<Exception> myFatalErrorConsumer = LOG::error;

    public UserIndexer(@NotNull VcsUserRegistryImpl registry) {
      myRegistry = registry;
    }

    @NotNull
    @Override
    public Map<Integer, Void> map(@NotNull VcsFullCommitDetails inputData) {
      Map<Integer, Void> result = new THashMap<>();

      try {
        result.put(myRegistry.getUserId(inputData.getAuthor()), null);
      }
      catch (IOException e) {
        myFatalErrorConsumer.consume(e);
      }

      return result;
    }

    public void setFatalErrorConsumer(@NotNull Consumer<Exception> fatalErrorConsumer) {
      myFatalErrorConsumer = fatalErrorConsumer;
    }
  }
}
