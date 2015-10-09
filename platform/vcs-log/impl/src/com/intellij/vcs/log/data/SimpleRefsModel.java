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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogRefs;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class SimpleRefsModel implements VcsLogRefs {
  @NotNull protected final Collection<VcsRef> myBranches;
  @NotNull private final MultiMap<CommitId, VcsRef> myRefsToHashes;

  protected SimpleRefsModel(@NotNull Iterable<VcsRef> allRefs) {
    myBranches = ContainerUtil.newSmartList();
    for (VcsRef ref : allRefs) {
      if (ref.getType().isBranch()) {
        myBranches.add(ref);
      }
    }

    myRefsToHashes = prepareRefsMap(allRefs);
  }

  @NotNull
  protected static MultiMap<CommitId, VcsRef> prepareRefsMap(@NotNull Iterable<VcsRef> refs) {
    MultiMap<CommitId, VcsRef> map = MultiMap.createSmart();
    for (VcsRef ref : refs) {
      map.putValue(new CommitId(ref.getCommitHash(), ref.getRoot()), ref);
    }
    return map;
  }

  @NotNull
  @Override
  public Collection<VcsRef> refsToCommit(@NotNull Hash hash, @NotNull VirtualFile root) {
    CommitId commitId = new CommitId(hash, root);
    if (myRefsToHashes.containsKey(commitId)) {
      return myRefsToHashes.get(commitId);
    }
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Collection<VcsRef> getBranches() {
    return myBranches;
  }

  @NotNull
  @Override
  public Collection<VcsRef> getAllRefs() {
    return ContainerUtil.newHashSet(myRefsToHashes.values());
  }
}

