// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogAggregatedStoredRefs;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.*;
import com.intellij.vcs.log.graph.GraphColorManagerImpl;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.api.printer.GraphColorGetter;
import com.intellij.vcs.log.graph.collapsing.CollapsedController;
import com.intellij.vcs.log.graph.impl.facade.BaseController;
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl;
import com.intellij.vcs.log.graph.impl.print.GraphColorGetterByHeadFactory;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class SnapshotVisiblePackBuilder {
  private static final int VISIBLE_RANGE = 1000;
  private final @NotNull VcsLogStorage myStorage;

  SnapshotVisiblePackBuilder(@NotNull VcsLogStorage storage) {
    myStorage = storage;
  }

  public @NotNull VisiblePack build(@NotNull VisiblePack visiblePack) {
    VcsLogGraphData dataPack = visiblePack.getDataPack();
    if (dataPack instanceof VcsLogGraphData.Error) {
      return visiblePack;
    }

    if (visiblePack.getVisibleGraph().getVisibleCommitCount() == 0 || !(visiblePack.getVisibleGraph() instanceof VisibleGraphImpl)) {
      VcsLogGraphData newDataPack = VcsLogGraphDataFactory.buildData(RefsModel.createEmptyInstance(myStorage),
                                                                     EmptyPermanentGraph.getInstance(),
                                                                     dataPack.getLogProviders(),
                                                                     false);
      if (visiblePack instanceof VisiblePack.ErrorVisiblePack) {
        return new VisiblePack.ErrorVisiblePack(newDataPack, visiblePack.getFilters(),
                                                ((VisiblePack.ErrorVisiblePack)visiblePack).getError());
      }
      return new VisiblePack(newDataPack, EmptyVisibleGraph.getInstance(), true, visiblePack.getFilters());
    }

    return build(dataPack, ((VisibleGraphImpl<Integer>)visiblePack.getVisibleGraph()), visiblePack.getFilters(),
                 visiblePack.getAdditionalData());
  }

  private @NotNull VisiblePack build(@NotNull VcsLogGraphData oldPack,
                                     @NotNull VisibleGraphImpl<Integer> oldGraph,
                                     @NotNull VcsLogFilterCollection filters,
                                     @NotNull Map<Key<?>, Object> data) {
    int visibleRow = VISIBLE_RANGE;
    int visibleRange = VISIBLE_RANGE;
    PermanentGraphInfo<Integer> info = oldGraph.buildSimpleGraphInfo(visibleRow, visibleRange);
    Set<Integer> heads = ContainerUtil.map2Set(info.getPermanentGraphLayout().getHeadNodeIndex(),
                                               integer -> info.getPermanentCommitsInfo().getCommitId(integer));

    VcsLogAggregatedStoredRefs
      newRefsModel = createRefsModel(oldPack.getRefsModel(), heads, oldGraph, oldPack.getLogProviders(), visibleRow, visibleRange);
    VcsLogGraphData newPack =
      VcsLogGraphDataFactory.buildData(newRefsModel, EmptyPermanentGraph.getInstance(), oldPack.getLogProviders(), false);
    GraphColorGetter colorGetter = new GraphColorGetterByHeadFactory<>(new GraphColorManagerImpl(newRefsModel)).createColorGetter(info);

    VisibleGraph<Integer> newGraph = new VisibleGraphImpl<>(new CollapsedController(new BaseController(info), info, null),
                                                            info, colorGetter);

    return new VisiblePack(newPack, newGraph, true, filters, data);
  }

  private VcsLogAggregatedStoredRefs createRefsModel(@NotNull VcsLogAggregatedStoredRefs refsModel,
                                                     @NotNull Set<Integer> heads,
                                                     @NotNull VisibleGraphImpl<Integer> visibleGraph,
                                                     @NotNull Map<VirtualFile, VcsLogProvider> providers,
                                                     int visibleRow,
                                                     int visibleRange) {
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
    return RefsModel.create(refs, myStorage, providers);
  }
}
