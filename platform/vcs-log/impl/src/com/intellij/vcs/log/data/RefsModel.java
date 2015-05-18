package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogHashMap;
import com.intellij.vcs.log.VcsLogRefs;
import com.intellij.vcs.log.VcsRef;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RefsModel implements VcsLogRefs {

  @NotNull private final Map<VirtualFile, Set<VcsRef>> myRefs;
  @NotNull private final VcsLogHashMap myHashMap;

  @NotNull private final Collection<VcsRef> myBranches;
  @NotNull private final MultiMap<Hash, VcsRef> myRefsToHashes;
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
      int index = myHashMap.getCommitIndex(ref.getCommitHash());
      SmartList<VcsRef> list = map.get(index);
      if (list == null) map.put(index, list = new SmartList<VcsRef>());
      list.add(ref);
    }
    return map;
  }

  @NotNull
  private static MultiMap<Hash, VcsRef> prepareRefsMap(@NotNull Collection<VcsRef> refs) {
    MultiMap<Hash, VcsRef> map = MultiMap.createSmart();
    for (VcsRef ref : refs) {
      map.putValue(ref.getCommitHash(), ref);
    }
    return map;
  }

  @NotNull
  public Collection<VcsRef> refsToCommit(@NotNull Hash hash) {
    if (myRefsToHashes.containsKey(hash)) {
      return myRefsToHashes.get(hash);
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
