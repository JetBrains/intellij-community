/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.visible;

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
      VisibleGraph<Integer> newGraph = EmptyVisibleGraph.getInstance();
      DataPackBase newPack = new DataPackBase(visiblePack.getDataPack().getLogProviders(), createEmptyRefsModel(), false);
      return new VisiblePack(newPack, newGraph, true, visiblePack.getFilters());
    }
  }

  @NotNull
  private VisiblePack build(@NotNull DataPackBase oldPack,
                            @NotNull VisibleGraphImpl<Integer> oldGraph,
                            @NotNull VcsLogFilterCollection filters,
                            @Nullable Object data) {
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

  @NotNull
  private RefsModel createEmptyRefsModel() {
    return new RefsModel(ContainerUtil.newHashMap(), ContainerUtil.newHashSet(), myStorage, ContainerUtil.newHashMap());
  }

  private RefsModel createRefsModel(@NotNull RefsModel refsModel,
                                    @NotNull Set<Integer> heads,
                                    @NotNull VisibleGraphImpl<Integer> visibleGraph,
                                    @NotNull Map<VirtualFile, VcsLogProvider> providers, int visibleRow, int visibleRange) {
    Set<VcsRef> branchesAndHeads = ContainerUtil.newHashSet();

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
    Map<VirtualFile, CompressedRefs> refs = ContainerUtil.newHashMap();
    for (VirtualFile root : providers.keySet()) {
      Set<VcsRef> refsForRoot = map.get(root);
      refs.put(root, new CompressedRefs(refsForRoot == null ? ContainerUtil.newHashSet() : refsForRoot, myStorage));
    }
    return new RefsModel(refs, heads, myStorage, providers);
  }
}
