package com.intellij.vcs.log.data;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefs;
import com.intellij.vcs.log.VcsRef;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RefsModel implements VcsLogRefs {
  private static final Logger LOG = Logger.getInstance(RefsModel.class);

  @NotNull private final VcsLogStorage myStorage;
  @NotNull private final Map<VirtualFile, CompressedRefs> myRefs;
  @NotNull private final TIntObjectHashMap<VcsRef> myBestRefForHead;
  @NotNull private final TIntObjectHashMap<VirtualFile> myRootForHead;

  public RefsModel(@NotNull Map<VirtualFile, CompressedRefs> refs,
                   @NotNull Set<Integer> heads,
                   @NotNull VcsLogStorage storage,
                   @NotNull Map<VirtualFile, VcsLogProvider> providers) {
    myRefs = refs;
    myStorage = storage;

    myBestRefForHead = new TIntObjectHashMap<>();
    myRootForHead = new TIntObjectHashMap<>();
    for (int head : heads) {
      CommitId commitId = myStorage.getCommitId(head);
      if (commitId != null) {
        VirtualFile root = commitId.getRoot();
        myRootForHead.put(head, root);
        Optional<VcsRef> bestRef =
          myRefs.get(root).refsToCommit(head).stream().min(providers.get(root).getReferenceManager().getBranchLayoutComparator());
        if (bestRef.isPresent()) {
          myBestRefForHead.put(head, bestRef.get());
        }
        else {
          LOG.debug("No references at head " + commitId);
        }
      }
    }
  }

  @Nullable
  public VcsRef bestRefToHead(int headIndex) {
    return myBestRefForHead.get(headIndex);
  }

  @NotNull
  public VirtualFile rootAtHead(int headIndex) {
    return myRootForHead.get(headIndex);
  }

  @NotNull
  public Map<VirtualFile, CompressedRefs> getAllRefsByRoot() {
    return myRefs;
  }

  public Collection<VcsRef> refsToCommit(int index) {
    CommitId id = myStorage.getCommitId(index);
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
}
