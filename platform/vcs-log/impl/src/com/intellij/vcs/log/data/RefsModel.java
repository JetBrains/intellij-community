package com.intellij.vcs.log.data;

import com.google.common.collect.Iterables;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.vcs.log.VcsLogHashMap;
import com.intellij.vcs.log.VcsRef;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RefsModel extends SimpleRefsModel {
  @NotNull private final Map<VirtualFile, Set<VcsRef>> myRefs;
  @NotNull private final VcsLogHashMap myHashMap;

  @NotNull private final TIntObjectHashMap<SmartList<VcsRef>> myRefsToIndices;

  public RefsModel(@NotNull Map<VirtualFile, Set<VcsRef>> refsByRoot, @NotNull VcsLogHashMap hashMap) {
    super(Iterables.concat(refsByRoot.values()));
    myRefs = refsByRoot;
    myHashMap = hashMap;

    myRefsToIndices = prepareRefsToIndicesMap(Iterables.concat(refsByRoot.values()));
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
  public Collection<VcsRef> refsToCommit(int index) {
    return myRefsToIndices.containsKey(index) ? myRefsToIndices.get(index) : Collections.<VcsRef>emptyList();
  }

  @NotNull
  public Map<VirtualFile, Set<VcsRef>> getAllRefsByRoot() {
    return myRefs;
  }

}
