// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.FixedHashMap;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogRefManager;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.RefsModel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;

@ApiStatus.Internal
public final class HeadCommitsComparator implements Comparator<Integer> {
  private static final Logger LOG = Logger.getInstance(HeadCommitsComparator.class);
  private final @NotNull RefsModel myRefsModel;
  private final @NotNull Map<VirtualFile, VcsLogRefManager> myRefManagers;
  private final @NotNull Function<? super Integer, ? extends Hash> myHashGetter;

  private final @NotNull Map<Integer, Integer> myErrorWasReported = new FixedHashMap<>(100);

  public HeadCommitsComparator(@NotNull RefsModel refsModel,
                               @NotNull Map<VirtualFile, VcsLogRefManager> refManagers,
                               @NotNull Function<? super Integer, ? extends Hash> hashGetter) {
    myRefsModel = refsModel;
    myRefManagers = refManagers;
    myHashGetter = hashGetter;
  }

  public void reportNoRefs(int head) {
    if (!myErrorWasReported.containsKey(head)) {
      myErrorWasReported.put(head, head);
      LOG.debug("No references found at head " + head + " which corresponds to hash " + myHashGetter.apply(head));
    }
  }

  @Override
  public int compare(Integer head1, Integer head2) {
    if (head1.equals(head2)) {
      return 0;
    }

    VcsRef ref1 = myRefsModel.bestRefToHead(head1);
    VcsRef ref2 = myRefsModel.bestRefToHead(head2);
    if (ref1 == null) {
      reportNoRefs(head1);
      if (ref2 == null) {
        reportNoRefs(head2);
        return head1 - head2;
      }
      return 1;
    }
    if (ref2 == null) {
      reportNoRefs(head2);
      return -1;
    }
    if (ref1.equals(ref2)) {
      LOG.warn("Different heads " + myHashGetter.apply(head1) + " and " + myHashGetter.apply(head2) + " contain the same reference " + ref1);
    }

    VirtualFile root1 = ref1.getRoot();
    VirtualFile root2 = ref2.getRoot();
    VcsLogRefManager refManager1 = myRefManagers.get(root1);
    VcsLogRefManager refManager2 = myRefManagers.get(root2);
    if (!refManager1.equals(refManager2)) {
      return refManager1.toString().compareTo(refManager2.toString());
    }

    return refManager1.getBranchLayoutComparator().compare(ref1, ref2);
  }
}
