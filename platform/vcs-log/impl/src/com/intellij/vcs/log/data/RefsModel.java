package com.intellij.vcs.log.data;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLogHashMap;
import com.intellij.vcs.log.VcsRef;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class RefsModel extends SimpleRefsModel {
  @NotNull private final Map<VirtualFile, Set<VcsRef>> myRefs;

  @NotNull private final TIntObjectHashMap<SmartList<VcsRef>> myBranchesToIndices;
  @NotNull private final TIntObjectHashMap<SmartList<VcsRef>> myRefsToHeadIndices;

  public RefsModel(@NotNull Map<VirtualFile, Set<VcsRef>> refsByRoot,
                   @NotNull final Set<Integer> heads,
                   @NotNull final VcsLogHashMap hashMap) {
    super(Iterables.concat(refsByRoot.values()));
    myRefs = refsByRoot;

    myBranchesToIndices = prepareRefsToIndicesMap(myBranches, hashMap);
    myRefsToHeadIndices = prepareRefsToIndicesMap(Iterables.filter(Iterables.concat(refsByRoot.values()), new Predicate<VcsRef>() {
      @Override
      public boolean apply(VcsRef vcsRef) {
        return heads.contains(hashMap.getCommitIndex(vcsRef.getCommitHash(), vcsRef.getRoot()));
      }
    }), hashMap);
  }

  private RefsModel(@NotNull Map<VirtualFile, Set<VcsRef>> refsByRoot,
                    @NotNull TIntObjectHashMap<SmartList<VcsRef>> branchesToIndices,
                    @NotNull TIntObjectHashMap<SmartList<VcsRef>> refsToIndices) {
    super(Iterables.concat(refsByRoot.values()));
    myRefs = refsByRoot;

    myBranchesToIndices = branchesToIndices;
    myRefsToHeadIndices = refsToIndices;
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
  public Collection<VcsRef> branchesToCommit(int index) {
    return myBranchesToIndices.containsKey(index) ? myBranchesToIndices.get(index) : Collections.<VcsRef>emptyList();
  }

  @NotNull
  public Collection<VcsRef> refsToHead(int headIndex) {
    return myRefsToHeadIndices.containsKey(headIndex) ? myRefsToHeadIndices.get(headIndex) : Collections.<VcsRef>emptyList();
  }

  @NotNull
  public Map<VirtualFile, Set<VcsRef>> getAllRefsByRoot() {
    return myRefs;
  }

}
