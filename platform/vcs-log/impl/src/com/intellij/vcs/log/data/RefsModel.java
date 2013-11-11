package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Condition;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author erokhins
 */
public class RefsModel {

  @NotNull private final Collection<VcsRef> myBranches;
  @NotNull private final MultiMap<Hash, VcsRef> myRefsToHashes;
  private final Function<Integer, Hash> myHashGetter;

  public RefsModel(@NotNull Collection<VcsRef> allRefs, Function<Integer, Hash> hashGetter) {
    myHashGetter = hashGetter;
    myBranches = ContainerUtil.filter(allRefs, new Condition<VcsRef>() {
      @Override
      public boolean value(VcsRef ref) {
        return ref.getType().isBranch();
      }
    });

    myRefsToHashes = prepareRefsMap(allRefs);
  }

  @NotNull
  private static MultiMap<Hash, VcsRef> prepareRefsMap(@NotNull Collection<VcsRef> refs) {
    MultiMap<Hash, VcsRef> map = MultiMap.create();
    for (VcsRef ref : refs) {
      map.putValue(ref.getCommitHash(), ref);
    }
    return map;
  }

  public boolean isBranchRef(@NotNull Hash commitHash) {
    for (VcsRef ref : refsToCommit(commitHash)) {
      if (ref.getType().isBranch()) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public Collection<VcsRef> refsToCommit(@NotNull Hash hash) {
    if (myRefsToHashes.containsKey(hash)) {
      return myRefsToHashes.get(hash);
    }
    return Collections.emptyList();
  }

  @NotNull
  public Collection<VcsRef> getBranches() {
    return myBranches;
  }

  @NotNull
  public Collection<VcsRef> getAllRefs() {
    return new ArrayList<VcsRef>(myRefsToHashes.values());
  }

  public boolean isBranchRef(int hash) {
    return isBranchRef(myHashGetter.fun(hash));
  }

}
