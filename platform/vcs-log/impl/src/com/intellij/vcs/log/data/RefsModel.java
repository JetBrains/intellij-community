package com.intellij.vcs.log.data;

import com.google.common.collect.Iterables;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
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
  @NotNull private final VcsLogHashMap myHashMap;

  @NotNull private final TIntObjectHashMap<SmartList<VcsRef>> myBranchesToIndices;
  @NotNull private final TIntObjectHashMap<SmartList<VcsRef>> myRefsToHeadIndices = new TIntObjectHashMap<SmartList<VcsRef>>();

  public RefsModel(@NotNull Map<VirtualFile, Set<VcsRef>> refsByRoot, @NotNull VcsLogHashMap hashMap) {
    super(Iterables.concat(refsByRoot.values()));
    myRefs = refsByRoot;
    myHashMap = hashMap;

    myBranchesToIndices = prepareRefsToIndicesMap(myBranches);
  }

  @NotNull
  private TIntObjectHashMap<SmartList<VcsRef>> prepareRefsToIndicesMap(@NotNull Iterable<VcsRef> refs) {
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
  public Collection<VcsRef> branchesToCommit(int index) {
    return myBranchesToIndices.containsKey(index) ? myBranchesToIndices.get(index) : Collections.<VcsRef>emptyList();
  }

  @NotNull
  public Collection<VcsRef> refsToHead(int headIndex) {
    if (myRefsToHeadIndices.containsKey(headIndex)) {
      return myRefsToHeadIndices.get(headIndex);
    }

    CommitId commitId = myHashMap.getCommitId(headIndex);
    Collection<VcsRef> refsToCommit = refsToCommit(commitId.getHash(), commitId.getRoot());
    myRefsToHeadIndices.put(headIndex, new SmartList<VcsRef>(refsToCommit));
    return refsToCommit;
  }

  @NotNull
  public Map<VirtualFile, Set<VcsRef>> getAllRefsByRoot() {
    return myRefs;
  }

}
