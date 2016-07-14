package com.intellij.vcs.log.data;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.vcs.log.*;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RefsModel implements VcsLogRefs {
  @NotNull private final VcsLogHashMap myHashMap;
  @NotNull private final Map<VirtualFile, CompressedRefs> myRefs;
  @NotNull private final TIntObjectHashMap<SmartList<VcsRef>> myHeadsMap;
  @NotNull private final TIntObjectHashMap<VirtualFile> myRootsToHeadIndices;

  public RefsModel(@NotNull Map<VirtualFile, CompressedRefs> refs,
                   @NotNull Set<Integer> heads,
                   @NotNull VcsLogHashMap hashMap) {
    myRefs = refs;
    myHashMap = hashMap;

    myRootsToHeadIndices = new TIntObjectHashMap<>();
    myHeadsMap = new TIntObjectHashMap<>();
    for (int head : heads) {
      CommitId commitId = myHashMap.getCommitId(head);
      if (commitId != null) {
        VirtualFile root = commitId.getRoot();
        myHeadsMap.put(head, myRefs.get(root).refsToCommit(head));
        myRootsToHeadIndices.put(head, root);
      }
    }
  }

  @NotNull
  public Collection<VcsRef> refsToHead(int index) {
    if (myHeadsMap.containsKey(index)) {
      return myHeadsMap.get(index);
    }
    return Collections.emptyList();
  }

  @NotNull
  public VirtualFile rootAtHead(int headIndex) {
    return myRootsToHeadIndices.get(headIndex);
  }

  @NotNull
  public Map<VirtualFile, CompressedRefs> getAllRefsByRoot() {
    return myRefs;
  }

  public Collection<VcsRef> refsToCommit(int index) {
    if (myHeadsMap.containsKey(index)) return myHeadsMap.get(index);
    CommitId id = myHashMap.getCommitId(index);
    if (id == null) return Collections.emptyList();
    VirtualFile root = id.getRoot();
    return myRefs.get(root).refsToCommit(index);
  }

  @Override
  @NotNull
  public Collection<VcsRef> getBranches() {
    return myRefs.values().stream().flatMap(CompressedRefs::streamBranches).collect(Collectors.toList());
  }

  @NotNull
  public Stream<VcsRef> stream() {
    assert !ApplicationManager.getApplication().isDispatchThread();
    return myRefs.values().stream().flatMap(CompressedRefs::stream);
  }

  @NotNull
  @Override
  public Collection<VcsRef> getAllRefs() {
    return stream().collect(Collectors.toList());
  }
}
