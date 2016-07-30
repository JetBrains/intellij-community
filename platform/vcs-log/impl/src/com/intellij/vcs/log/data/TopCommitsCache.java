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
package com.intellij.vcs.log.data;

import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.VcsLogHashMap;
import com.intellij.vcs.log.impl.VcsCommitMetadataImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class TopCommitsCache {
  @NotNull private final ConcurrentIntObjectMap<VcsCommitMetadata> myCache = ContainerUtil.createConcurrentIntObjectMap();
  @NotNull private final VcsLogHashMap myHashMap;

  public TopCommitsCache(@NotNull VcsLogHashMap hashMap) {
    myHashMap = hashMap;
  }

  public void storeDetails(@NotNull Collection<? extends VcsCommitMetadata> metadatas) {
    for (VcsCommitMetadata detail : metadatas) {
      myCache.put(myHashMap.getCommitIndex(detail.getId(), detail.getRoot()), detail);
    }
  }

  public void putDetails(int id, @NotNull VcsCommitMetadataImpl metadata) {
    myCache.put(id, metadata);
  }

  @Nullable
  public VcsCommitMetadata get(int index) {
    return myCache.get(index);
  }

  public void clear() {
    myCache.clear();
  }
}
