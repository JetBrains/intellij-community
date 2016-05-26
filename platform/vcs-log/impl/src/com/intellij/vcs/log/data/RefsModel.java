package com.intellij.vcs.log.data;

import com.google.common.collect.Iterables;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.*;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class RefsModel implements VcsLogRefs {
  @NotNull private final Collection<VcsRef> myBranches;
  @NotNull private final Map<VirtualFile, Set<VcsRef>> myRefs;
  @NotNull private final MultiMap<CommitId, VcsRef> myRefsToHashes;

  @NotNull private final TIntObjectHashMap<SmartList<VcsRef>> myBranchesToIndices;
  @NotNull private final TIntObjectHashMap<SmartList<VcsRef>> myRefsToHeadIndices;
  @NotNull private final TIntObjectHashMap<VirtualFile> myRootsToHeadIndices;

  public RefsModel(@NotNull Map<VirtualFile, Set<VcsRef>> refsByRoot,
                   @NotNull final Set<Integer> heads,
                   @NotNull final VcsLogHashMap hashMap) {
    myRefs = refsByRoot;

    Iterable<VcsRef> allRefs = Iterables.concat(refsByRoot.values());

    myBranches = ContainerUtil.newSmartList();
    for (VcsRef ref : allRefs) {
      if (ref.getType().isBranch()) {
        myBranches.add(ref);
      }
    }

    myRefsToHashes = prepareRefsMap(allRefs);

    myBranchesToIndices = prepareRefsToIndicesMap(myBranches, hashMap);
    myRefsToHeadIndices = prepareRefsToIndicesMap(Iterables.filter(Iterables.concat(refsByRoot.values()),
                                                                   vcsRef -> heads.contains(
                                                                     hashMap.getCommitIndex(vcsRef.getCommitHash(), vcsRef.getRoot()))),
                                                  hashMap);

    myRootsToHeadIndices = prepareRootsMap(heads, hashMap);
  }

  @NotNull
  private static TIntObjectHashMap<VirtualFile> prepareRootsMap(@NotNull Set<Integer> heads, @NotNull VcsLogHashMap hashMap) {
    TIntObjectHashMap<VirtualFile> map = new TIntObjectHashMap<VirtualFile>();
    for (Integer head : heads) {
      CommitId commitId = hashMap.getCommitId(head);
      if (commitId != null) {
        map.put(head, commitId.getRoot());
      }
    }
    return map;
  }

  @NotNull
  private static TIntObjectHashMap<SmartList<VcsRef>> prepareRefsToIndicesMap(@NotNull Iterable<VcsRef> refs,
                                                                              @NotNull VcsLogHashMap hashMap) {
    TIntObjectHashMap<SmartList<VcsRef>> map = new TIntObjectHashMap<SmartList<VcsRef>>();
    for (VcsRef ref : refs) {
      int index = hashMap.getCommitIndex(ref.getCommitHash(), ref.getRoot());
      SmartList<VcsRef> list = map.get(index);
      if (list == null) map.put(index, list = new SmartList<VcsRef>());
      list.add(ref);
    }
    return map;
  }

  @NotNull
  private static MultiMap<CommitId, VcsRef> prepareRefsMap(@NotNull Iterable<VcsRef> refs) {
    MultiMap<CommitId, VcsRef> map = MultiMap.createSmart();
    for (VcsRef ref : refs) {
      map.putValue(new CommitId(ref.getCommitHash(), ref.getRoot()), ref);
    }
    return map;
  }

  @NotNull
  public Collection<VcsRef> branchesToCommit(int index) {
    return myBranchesToIndices.containsKey(index) ? myBranchesToIndices.get(index) : Collections.<VcsRef>emptyList();
  }

  @NotNull
  public Collection<VcsRef> refsToHead(int headIndex) {
    return myRefsToHeadIndices.containsKey(headIndex) ? myRefsToHeadIndices.get(headIndex) : Collections.<VcsRef>emptyList();
  }

  @NotNull
  public VirtualFile rootAtHead(int headIndex) {
    return myRootsToHeadIndices.get(headIndex);
  }

  @NotNull
  public Map<VirtualFile, Set<VcsRef>> getAllRefsByRoot() {
    return myRefs;
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
