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
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefs;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.DataPackBase;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class VisiblePack implements VcsLogDataPack {
  @NotNull
  public static final VisiblePack EMPTY =
    new VisiblePack(DataPack.EMPTY, EmptyVisibleGraph.getInstance(), false, VcsLogFilterCollectionImpl.EMPTY) {
      @Override
      public String toString() {
        return "EmptyVisiblePack";
      }
    };

  @NotNull private final DataPackBase myDataPack;
  @NotNull private final VisibleGraph<Integer> myVisibleGraph;
  private final boolean myCanRequestMore;
  @NotNull private final VcsLogFilterCollection myFilters;

  public VisiblePack(@NotNull DataPackBase dataPack,
                     @NotNull VisibleGraph<Integer> graph,
                     boolean canRequestMore,
                     @NotNull VcsLogFilterCollection filters) {
    myDataPack = dataPack;
    myVisibleGraph = graph;
    myCanRequestMore = canRequestMore;
    myFilters = filters;
  }

  @NotNull
  public VisibleGraph<Integer> getVisibleGraph() {
    return myVisibleGraph;
  }

  @NotNull
  public DataPackBase getDataPack() {
    return myDataPack;
  }

  public boolean canRequestMore() {
    return myCanRequestMore;
  }

  @NotNull
  @Override
  public Map<VirtualFile, VcsLogProvider> getLogProviders() {
    return myDataPack.getLogProviders();
  }

  @NotNull
  @Override
  public VcsLogRefs getRefs() {
    return myDataPack.getRefsModel();
  }

  public boolean isFull() {
    return myDataPack.isFull();
  }

  @Override
  @NotNull
  public VcsLogFilterCollection getFilters() {
    return myFilters;
  }

  public VirtualFile getRoot(int row) {
    int head = myVisibleGraph.getRowInfo(row).getOneOfHeads();
    return myDataPack.getRefsModel().rootAtHead(head);
  }

  @Override
  public String toString() {
    return "VisiblePack{size=" +
           myVisibleGraph.getVisibleCommitCount() +
           ", filters=" +
           myFilters +
           ", canRequestMore=" +
           myCanRequestMore + "}";
  }
}
