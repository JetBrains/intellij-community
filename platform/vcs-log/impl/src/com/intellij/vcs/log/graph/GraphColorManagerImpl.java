// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogRefManager;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.RefsModel;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Map;

public final class GraphColorManagerImpl implements GraphColorManager<Integer> {
  private static final Logger LOG = Logger.getInstance(GraphColorManagerImpl.class);
  static final int DEFAULT_COLOR = 0;

  @NotNull private final HeadsComparator myHeadsComparator;
  @NotNull private final RefsModel myRefsModel;

  public GraphColorManagerImpl(@NotNull RefsModel refsModel,
                               @NotNull Function<Integer, Hash> hashGetter,
                               @NotNull Map<VirtualFile, VcsLogRefManager> refManagers) {
    myRefsModel = refsModel;

    myHeadsComparator = new HeadsComparator(refsModel, refManagers, hashGetter);
  }

  @Override
  public int getColorOfBranch(Integer headCommit) {
    VcsRef firstRef = myRefsModel.bestRefToHead(headCommit);
    if (firstRef == null) {
      return DEFAULT_COLOR;
    }
    // TODO dark variant
    return firstRef.getName().hashCode();
  }

  @Override
  public int getColorOfFragment(Integer headCommit, int magicIndex) {
    return magicIndex;
  }

  @Override
  public int compareHeads(Integer head1, Integer head2) {
    return myHeadsComparator.compare(head1, head2);
  }

  public static final class HeadsComparator implements Comparator<Integer> {
    @NotNull private final RefsModel myRefsModel;
    @NotNull private final Map<VirtualFile, VcsLogRefManager> myRefManagers;
    @NotNull private final Function<? super Integer, ? extends Hash> myHashGetter;

    @NotNull private final LinkedHashMap<Integer, Integer> myErrorWasReported = new LinkedHashMap<>(10) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
        return size() > 100;
      }
    };

    public HeadsComparator(@NotNull RefsModel refsModel,
                           @NotNull Map<VirtualFile, VcsLogRefManager> refManagers,
                           @NotNull Function<? super Integer, ? extends Hash> hashGetter) {
      myRefsModel = refsModel;
      myRefManagers = refManagers;
      myHashGetter = hashGetter;
    }

    public void reportNoRefs(int head) {
      if (!myErrorWasReported.containsKey(head)) {
        myErrorWasReported.put(head, head);
        LOG.debug("No references found at head " + head + " which corresponds to hash " + myHashGetter.fun(head));
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
        LOG.warn("Different heads " + myHashGetter.fun(head1) + " and " + myHashGetter.fun(head2) + " contain the same reference " + ref1);
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
}
