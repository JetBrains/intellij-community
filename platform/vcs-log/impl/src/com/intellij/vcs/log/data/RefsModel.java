// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefs;
import com.intellij.vcs.log.VcsRef;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RefsModel implements VcsLogRefs {
  private static final Logger LOG = Logger.getInstance(RefsModel.class);

  private final @NotNull VcsLogStorage myStorage;
  private final @NotNull Map<VirtualFile, CompressedRefs> myRefs;
  private final @NotNull Int2ObjectMap<VcsRef> myBestRefForHead;
  private final @NotNull Int2ObjectMap<VirtualFile> myRootForHead;

  public RefsModel(@NotNull Map<VirtualFile, CompressedRefs> refs,
                   @NotNull Set<Integer> heads,
                   @NotNull VcsLogStorage storage,
                   @NotNull Map<VirtualFile, VcsLogProvider> providers) {
    myRefs = refs;
    myStorage = storage;

    myBestRefForHead = new Int2ObjectOpenHashMap<>();
    myRootForHead = new Int2ObjectOpenHashMap<>();
    Map<@NotNull Integer, @NotNull CommitId> commitIds = myStorage.getCommitIds(heads);
    for (int head : heads) {
      CommitId commitId = commitIds.get(head);
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

  public @Nullable VcsRef bestRefToHead(int headIndex) {
    return myBestRefForHead.get(headIndex);
  }

  public @Nullable VirtualFile rootAtHead(int headIndex) {
    return myRootForHead.get(headIndex);
  }

  public @NotNull Map<VirtualFile, CompressedRefs> getAllRefsByRoot() {
    return myRefs;
  }

  public @NotNull List<VcsRef> refsToCommit(int index) {
    if (myRefs.size() <= 10) {
      for (CompressedRefs refs : myRefs.values()) {
        if (refs.contains(index)) {
          return refs.refsToCommit(index);
        }
      }
      return Collections.emptyList();
    }
    CommitId id = myStorage.getCommitId(index);
    if (id == null) return Collections.emptyList();
    VirtualFile root = id.getRoot();
    return myRefs.get(root).refsToCommit(index);
  }

  @Override
  public @NotNull Collection<VcsRef> getBranches() {
    return myRefs.values().stream().flatMap(CompressedRefs::streamBranches).collect(Collectors.toList());
  }

  @Override
  public @NotNull Stream<VcsRef> stream() {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    return myRefs.values().stream().flatMap(CompressedRefs::stream);
  }

  public static @NotNull RefsModel createEmptyInstance(@NotNull VcsLogStorage storage) {
    return new RefsModel(Collections.emptyMap(), Collections.emptySet(), storage, Collections.emptyMap());
  }
}
