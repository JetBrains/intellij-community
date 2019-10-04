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

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefs;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.DataPackBase;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.history.FileHistoryPaths;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class VisiblePack implements VcsLogDataPack {
  @NotNull
  public static final VisiblePack EMPTY =
    new VisiblePack(DataPack.EMPTY, EmptyVisibleGraph.getInstance(), false, VcsLogFilterObject.EMPTY_COLLECTION) {
      @Override
      public String toString() {
        return "EmptyVisiblePack";
      }
    };

  @NotNull private final DataPackBase myDataPack;
  @NotNull private final VisibleGraph<Integer> myVisibleGraph;
  private final boolean myCanRequestMore;
  @NotNull private final VcsLogFilterCollection myFilters;
  @Nullable private final Object myAdditionalData;

  public VisiblePack(@NotNull DataPackBase dataPack,
                     @NotNull VisibleGraph<Integer> graph,
                     boolean canRequestMore,
                     @NotNull VcsLogFilterCollection filters) {
    this(dataPack, graph, canRequestMore, filters, null);
  }

  public VisiblePack(@NotNull DataPackBase dataPack,
                     @NotNull VisibleGraph<Integer> graph,
                     boolean canRequestMore,
                     @NotNull VcsLogFilterCollection filters,
                     @Nullable Object data) {
    myDataPack = dataPack;
    myVisibleGraph = graph;
    myCanRequestMore = canRequestMore;
    myFilters = filters;
    myAdditionalData = data;
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

  @Override
  public boolean isEmpty() {
    return getVisibleGraph().getVisibleCommitCount() == 0;
  }

  public VirtualFile getRoot(int row) {
    int head = myVisibleGraph.getRowInfo(row).getOneOfHeads();
    return myDataPack.getRefsModel().rootAtHead(head);
  }

  public <T> T getAdditionalData() {
    return (T)myAdditionalData;
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

  @NotNull
  public FilePath getFilePath(int index) {
    if (FileHistoryPaths.hasPathsInformation(this)) {
      FilePath path = FileHistoryPaths.filePathOrDefault(this, myVisibleGraph.getRowInfo(index).getCommit());
      if (path != null) {
        return path;
      }
    }
    return VcsUtil.getFilePath(getRoot(index));
  }

  public static class ErrorVisiblePack extends VisiblePack {
    @NotNull private final Throwable myError;

    public ErrorVisiblePack(@NotNull DataPackBase dataPack, @NotNull VcsLogFilterCollection filters, @NotNull Throwable error) {
      super(dataPack, EmptyVisibleGraph.getInstance(), false, filters, null);
      myError = error;
    }

    @NotNull
    public Throwable getError() {
      return myError;
    }
  }
}
