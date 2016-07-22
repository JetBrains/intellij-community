package com.intellij.vcs.log.data;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class RefsModel implements VcsLogRefs {
  @NotNull private final Collection<VcsRef> myBranches;
  @NotNull private final Map<VirtualFile, Set<VcsRef>> myRefs;
  @NotNull private final VcsLogHashMap myHashMap;
  @NotNull private final TIntObjectHashMap<SmartList<VcsRef>> myRefsToHashes;
  @NotNull private final TIntObjectHashMap<VirtualFile> myRootsToHeadIndices;

  public RefsModel(@NotNull Map<VirtualFile, Set<VcsRef>> refsByRoot,
                   @NotNull final Set<Integer> heads,
                   @NotNull final VcsLogHashMap hashMap) {
    myRefs = refsByRoot;
    myHashMap = hashMap;

    Iterable<VcsRef> allRefs = Iterables.concat(refsByRoot.values());

    myBranches = ContainerUtil.newSmartList();
    for (VcsRef ref : allRefs) {
      if (ref.getType().isBranch()) {
        myBranches.add(ref);
      }
    }

    myRefsToHashes = prepareRefsToIndicesMap(allRefs, hashMap);
    myRootsToHeadIndices = prepareRootsMap(heads, hashMap);
  }

  @NotNull
  private static TIntObjectHashMap<VirtualFile> prepareRootsMap(@NotNull Set<Integer> heads, @NotNull VcsLogHashMap hashMap) {
    TIntObjectHashMap<VirtualFile> map = new TIntObjectHashMap<>();
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
    TIntObjectHashMap<SmartList<VcsRef>> map = new TIntObjectHashMap<>();
    for (VcsRef ref : refs) {
      int index = hashMap.getCommitIndex(ref.getCommitHash(), ref.getRoot());
      SmartList<VcsRef> list = map.get(index);
      if (list == null) map.put(index, list = new SmartList<>());
      list.add(ref);
    }
    return map;
  }

  @NotNull
  public Collection<VcsRef> branchesToCommit(int index) {
    Collection<VcsRef> refs = refsToCommit(index);
    return ContainerUtil.filter(refs, ref -> ref.getType().isBranch());
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
    return refsToCommit(myHashMap.getCommitIndex(hash, root));
  }

  public Collection<VcsRef> refsToCommit(int index) {
    if (myRefsToHashes.containsKey(index)) {
      return myRefsToHashes.get(index);
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
    return new AbstractCollection<VcsRef>() {
      @Override
      public Iterator<VcsRef> iterator() {
        List<Iterator<VcsRef>> iterators = myRefs.values().stream().map(Set::iterator).collect(Collectors.toList());
        return Iterators.concat(iterators.iterator());
      }

      @Override
      public int size() {
        return myRefs.values().stream().mapToInt(Set::size).sum();
      }
    };
  }
}
