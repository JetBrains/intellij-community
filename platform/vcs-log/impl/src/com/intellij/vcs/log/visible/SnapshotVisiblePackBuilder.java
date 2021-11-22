// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.*;
import com.intellij.vcs.log.graph.GraphColorManagerImpl;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.collapsing.CollapsedController;
import com.intellij.vcs.log.graph.impl.facade.BaseController;
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SnapshotVisiblePackBuilder {
  private static final int VISIBLE_RANGE = 1000;
  @NotNull private final VcsLogStorage myStorage;

  public SnapshotVisiblePackBuilder(@NotNull VcsLogStorage storage) {
    myStorage = storage;
  }

  @NotNull
  public VisiblePack build(@NotNull VisiblePack visiblePack) {
    if (visiblePack.getVisibleGraph() instanceof VisibleGraphImpl && visiblePack.getVisibleGraph().getVisibleCommitCount() > 0) {
      return build(visiblePack.getDataPack(), ((VisibleGraphImpl<Integer>)visiblePack.getVisibleGraph()), visiblePack.getFilters(),
                   visiblePack.getAdditionalData());
    }
    else {
      DataPackBase newPack = new DataPackBase(visiblePack.getDataPack().getLogProviders(),
                                              RefsModel.createEmptyInstance(myStorage), false);
      return new VisiblePack(newPack, EmptyVisibleGraph.getInstance(), true, visiblePack.getFilters());
    }
  }

  @NotNull
  private VisiblePack build(@NotNull DataPackBase oldPack,
                            @NotNull VisibleGraphImpl<Integer> oldGraph,
                            @NotNull VcsLogFilterCollection filters,
                            @Nullable Map<Key, Object> data) {
    int visibleRow = VISIBLE_RANGE;
    int visibleRange = VISIBLE_RANGE;
    PermanentGraphInfo<Integer> info = oldGraph.buildSimpleGraphInfo(visibleRow, visibleRange);
    Set<Integer> heads = ContainerUtil.map2Set(info.getPermanentGraphLayout().getHeadNodeIndex(),
                                               integer -> info.getPermanentCommitsInfo().getCommitId(integer));

    RefsModel newRefsModel = createRefsModel(oldPack.getRefsModel(), heads, oldGraph, oldPack.getLogProviders(), visibleRow, visibleRange);
    DataPackBase newPack = new DataPackBase(oldPack.getLogProviders(), newRefsModel, false);

    GraphColorManagerImpl colorManager =
      new GraphColorManagerImpl(newRefsModel, VcsLogStorageImpl.createHashGetter(myStorage),
                                DataPack.getRefManagerMap(oldPack.getLogProviders()));

    VisibleGraph<Integer> newGraph =
      new VisibleGraphImpl<>(new CollapsedController(new BaseController(info), info, null), info, colorManager);

    return new VisiblePack(newPack, newGraph, true, filters, data);
  }

  private RefsModel createRefsModel(@NotNull RefsModel refsModel,
                                    @NotNull Set<Integer> heads,
                                    @NotNull VisibleGraphImpl<Integer> visibleGraph,
                                    @NotNull Map<VirtualFile, VcsLogProvider> providers, int visibleRow, int visibleRange) {
    Set<VcsRef> branchesAndHeads = new HashSet<>();

    for (int row = Math.max(0, visibleRow - visibleRange);
         row < Math.min(visibleGraph.getLinearGraph().nodesCount(), visibleRow + visibleRange);
         row++) {
      Integer commit = visibleGraph.getRowInfo(row).getCommit();
      refsModel.refsToCommit(commit).forEach(ref -> {
        if (ref.getType().isBranch() || heads.contains(commit)) {
          branchesAndHeads.add(ref);
        }
      });
    }

    Map<VirtualFile, Set<VcsRef>> map = VcsLogUtil.groupRefsByRoot(branchesAndHeads);
    Map<VirtualFile, CompressedRefs> refs = new HashMap<>();
    for (VirtualFile root : providers.keySet()) {
      Set<VcsRef> refsForRoot = map.get(root);
      refs.put(root, new CompressedRefs(refsForRoot == null ? new HashSet<>() : refsForRoot, myStorage));
    }
    return new RefsModel(refs, heads, myStorage, providers);
  }
}
