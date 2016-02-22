package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.*;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RefsModel implements VcsLogRefs {

  @NotNull private final Map<VirtualFile, Set<VcsRef>> myRefs;
  @NotNull private final VcsLogHashMap myHashMap;

  @NotNull private final Collection<VcsRef> myBranches;
  @NotNull private final MultiMap<CommitId, VcsRef> myRefsToHashes;
  @NotNull private final TIntObjectHashMap<SmartList<VcsRef>> myRefsToIndices;

  public RefsModel(@NotNull Map<VirtualFile, Set<VcsRef>> refsByRoot, @NotNull VcsLogHashMap hashMap) {
    myRefs = refsByRoot;
    myHashMap = hashMap;

    List<VcsRef> allRefs = ContainerUtil.concat(refsByRoot.values());
    myBranches = ContainerUtil.filter(allRefs, new Condition<VcsRef>() {
      @Override
      public boolean value(VcsRef ref) {
        return ref.getType().isBranch();
      }
    });

    myRefsToHashes = prepareRefsMap(allRefs);
    myRefsToIndices = prepareRefsToIndicesMap(allRefs);
  }

  @NotNull
  private TIntObjectHashMap<SmartList<VcsRef>> prepareRefsToIndicesMap(@NotNull Collection<VcsRef> refs) {
    TIntObjectHashMap<SmartList<VcsRef>> map = new TIntObjectHashMap<SmartList<VcsRef>>();
    for (VcsRef ref : refs) {
      int index = myHashMap.getCommitIndex(ref.getCommitHash(), ref.getRoot());
      SmartList<VcsRef> list = map.get(index);
      if (list == null) map.put(index, list = new SmartList<VcsRef>());
      list.add(ref);
    }
    return map;
  }

  @NotNull
  private static MultiMap<CommitId, VcsRef> prepareRefsMap(@NotNull Collection<VcsRef> refs) {
    MultiMap<CommitId, VcsRef> map = MultiMap.createSmart();
    for (VcsRef ref : refs) {
      map.putValue(new CommitId(ref.getCommitHash(), ref.getRoot()), ref);
    }
    return map;
  }

  @NotNull
  public Collection<VcsRef> refsToCommit(@NotNull Hash hash, @NotNull VirtualFile root) {
    CommitId commitId = new CommitId(hash, root);
    if (myRefsToHashes.containsKey(commitId)) {
      return myRefsToHashes.get(commitId);
    }
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Collection<VcsRef> refsToCommit(int index) {
    return myRefsToIndices.containsKey(index) ? myRefsToIndices.get(index) : Collections.<VcsRef>emptyList();
  }

  @Override
  @NotNull
  public Collection<VcsRef> getBranches() {
    return myBranches;
  }

  @NotNull
  public Collection<VcsRef> getAllRefs() {
    return new ArrayList<VcsRef>(myRefsToHashes.values());
  }

  @NotNull
  public Map<VirtualFile, Set<VcsRef>> getAllRefsByRoot() {
    return myRefs;
  }

}
