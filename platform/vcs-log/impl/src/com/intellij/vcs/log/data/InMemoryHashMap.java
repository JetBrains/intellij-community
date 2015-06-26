/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Enumerator;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogHashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectHashingStrategy;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InMemoryHashMap implements VcsLogHashMap {
  private final Enumerator<CommitId> myEnumerator = new Enumerator<CommitId>(1, TObjectHashingStrategy.CANONICAL);
  private final TIntObjectHashMap<CommitId> myIntToObjectMap = new TIntObjectHashMap<CommitId>();

  @Override
  public int getCommitIndex(@NotNull Hash hash, @NotNull VirtualFile root) {
    return getOrPut(hash, root);
  }

  private int getOrPut(@NotNull Hash hash, @NotNull VirtualFile root) {
    CommitId commitId = new CommitId(hash, root);
    int index = myEnumerator.enumerate(commitId);
    myIntToObjectMap.put(index, commitId);
    return index;
  }

  @NotNull
  @Override
  public CommitId getCommitId(int commitIndex) {
    CommitId hash = myIntToObjectMap.get(commitIndex);
    if (hash == null) {
      throw new RuntimeException("Can not find hash by index " + commitIndex);
    }
    return hash;
  }

  @Nullable
  @Override
  public CommitId findCommitId(@NotNull final Condition<CommitId> condition) {
    final CommitId[] result = new CommitId[]{null};
    myIntToObjectMap.forEachValue(new TObjectProcedure<CommitId>() {
      @Override
      public boolean execute(CommitId commitId) {
        if (condition.value(commitId)) {
          result[0] = commitId;
          return false;
        }
        return true;
      }
    });
    return result[0];
  }

  @Override
  public void flush() {
    // nothing to flush
  }
}
