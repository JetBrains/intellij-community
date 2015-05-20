/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogRefManager;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.RefsModel;
import com.intellij.vcs.log.impl.VcsLogUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class GraphColorManagerImpl implements GraphColorManager<Integer> {

  private static final Logger LOG = Logger.getInstance(GraphColorManagerImpl.class);
  static final int DEFAULT_COLOR = 0;

  @NotNull private final RefsModel myRefsModel;
  @NotNull private final NotNullFunction<Integer, Hash> myHashGetter;
  @NotNull private final Map<VirtualFile, VcsLogRefManager> myRefManagers;

  @NotNull private final LinkedHashMap<Integer, Integer> myErrorWasReported = new LinkedHashMap<Integer, Integer>(10) {

    @Override
    protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
      return size() > 100;
    }
  };

  public GraphColorManagerImpl(@NotNull RefsModel refsModel, @NotNull NotNullFunction<Integer, Hash> hashGetter,
                               @NotNull Map<VirtualFile, VcsLogRefManager> refManagers) {
    myRefsModel = refsModel;
    myHashGetter = hashGetter;
    myRefManagers = refManagers;
  }

  @Override
  public int getColorOfBranch(Integer headCommit) {
    Collection<VcsRef> refs = myRefsModel.refsToCommit(headCommit);
    if (isEmptyRefs(refs, headCommit)) {
      return DEFAULT_COLOR;
    }
    VcsRef firstRef = Collections.min(refs, myRefManagers.get(getFirstRoot(refs)).getBranchLayoutComparator());
    // TODO dark variant
    return firstRef.getName().hashCode();
  }

  @NotNull
  private VirtualFile getFirstRoot(@NotNull Collection<VcsRef> refs) {
    return refs.iterator().next().getRoot();
  }

  private boolean isEmptyRefs(@NotNull Collection<VcsRef> refs, int head) {
    if (refs.isEmpty()) {
      if (!myErrorWasReported.containsKey(head)) {
        myErrorWasReported.put(head, head);
        LOG.warn("No references found at head " + head + " which corresponds to hash " + myHashGetter.fun(head));
      }
      return true;
    }
    return false;
  }

  @Override
  public int getColorOfFragment(Integer headCommit, int magicIndex) {
    return magicIndex;
  }

  @Override
  public int compareHeads(Integer head1, Integer head2) {
    if (head1.equals(head2)) {
      return 0;
    }

    Collection<VcsRef> refs1 = myRefsModel.refsToCommit(head1);
    Collection<VcsRef> refs2 = myRefsModel.refsToCommit(head2);
    boolean firstEmpty = isEmptyRefs(refs1, head1);
    boolean secondEmpty = isEmptyRefs(refs2, head2);
    if (firstEmpty && secondEmpty) {
      return head1 - head2;
    }
    if (firstEmpty) {
      return 1;
    }
    if (secondEmpty) {
      return -1;
    }

    VirtualFile root1 = getFirstRoot(refs1);
    VirtualFile root2 = getFirstRoot(refs2);
    VcsLogRefManager refManager1 = myRefManagers.get(root1);
    VcsLogRefManager refManager2 = myRefManagers.get(root2);
    if (!refManager1.equals(refManager2)) {
      return VcsLogUtil.compareRoots(root1, root2);
    }

    VcsRef bestRef = ContainerUtil.sorted(ContainerUtil.concat(refs1, refs2), refManager1.getBranchLayoutComparator()).get(0);
    return refs1.contains(bestRef) ? -1 : 1;
  }
}
