/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogHashMap;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.collapsing.CollapsedController;
import com.intellij.vcs.log.graph.impl.facade.BaseController;
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class FakeVisiblePackBuilder {
  @NotNull private final VcsLogHashMap myHashMap;

  public FakeVisiblePackBuilder(@NotNull VcsLogHashMap hashMap) {
    myHashMap = hashMap;
  }

  @NotNull
  public VisiblePack build(@NotNull VisiblePack visiblePack) {
    VisibleGraph<Integer> newGraph;
    Set<Integer> heads;
    if (visiblePack.getVisibleGraph() instanceof VisibleGraphImpl) {
      final PermanentGraphInfo<Integer> info = ((VisibleGraphImpl<Integer>)visiblePack.getVisibleGraph()).buildSimpleGraphInfo();
      newGraph = new VisibleGraphImpl<Integer>(new CollapsedController(new BaseController(info), info, null), info);
      heads = ContainerUtil.map2Set(info.getPermanentGraphLayout().getHeadNodeIndex(), new Function<Integer, Integer>() {
        @Override
        public Integer fun(Integer integer) {
          return info.getPermanentCommitsInfo().getCommitId(integer);
        }
      });
    }
    else {
      newGraph = EmptyVisibleGraph.getInstance();
      heads = Collections.emptySet();
    }

    DataPackBase oldPack = visiblePack.getDataPack();
    RefsModel newRefsModel = createRefsModel(oldPack.getRefsModel(), heads, visiblePack);
    DataPackBase newPack = new DataPackBase(oldPack.getLogProviders(), newRefsModel, false);

    return new VisiblePack(newPack, newGraph, true, visiblePack.getFilters());
  }

  private RefsModel createRefsModel(@NotNull RefsModel refsModel, @NotNull Set<Integer> heads, VisiblePack visiblePack) {
    Map<VirtualFile, Set<VcsRef>> refs = ContainerUtil.newHashMap();
    Collection<VcsRef> allRefs = refsModel.getAllRefs();
    for (VcsRef ref : allRefs) {
      int commitIndex = myHashMap.getCommitIndex(ref.getCommitHash(), ref.getRoot());
      if (ref.getType().isBranch() || heads.contains(commitIndex)) {
        Integer row = visiblePack.getVisibleGraph().getVisibleRowIndex(commitIndex);
        if (row != null && row >= 0) {
          Set<VcsRef> refsByRoot = refs.get(ref.getRoot());
          if (refsByRoot == null) {
            refsByRoot = ContainerUtil.newHashSet();
            refs.put(ref.getRoot(), refsByRoot);
          }
          refsByRoot.add(ref);
        }
      }
    }
    return new RefsModel(refs, heads, myHashMap);
  }
}
