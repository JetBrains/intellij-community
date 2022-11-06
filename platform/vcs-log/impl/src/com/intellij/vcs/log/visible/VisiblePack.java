// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.VcsLogRefs;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.DataPackBase;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VisiblePack implements VcsLogDataPack, UserDataHolder {
  @NotNull
  public static final VisiblePack EMPTY =
    new VisiblePack(DataPack.EMPTY, EmptyVisibleGraph.getInstance(), false, VcsLogFilterObject.EMPTY_COLLECTION) {
      @Override
      public String toString() {
        return "EmptyVisiblePack";
      }
    };

  @NotNull
  public static final Key<Boolean> NO_GRAPH_INFORMATION = Key.create("NO_GRAPH_INFORMATION");

  @NotNull private final DataPackBase myDataPack;
  @NotNull private final VisibleGraph<Integer> myVisibleGraph;
  private final boolean myCanRequestMore;
  @NotNull private final VcsLogFilterCollection myFilters;
  @NotNull private final Map<Key, Object> myAdditionalData = new ConcurrentHashMap<>();

  public VisiblePack(@NotNull DataPackBase dataPack,
                     @NotNull VisibleGraph<Integer> graph,
                     boolean canRequestMore,
                     @NotNull VcsLogFilterCollection filters) {
    this(dataPack, graph, canRequestMore, filters, Collections.emptyMap());
  }

  public VisiblePack(@NotNull DataPackBase dataPack,
                     @NotNull VisibleGraph<Integer> graph,
                     boolean canRequestMore,
                     @NotNull VcsLogFilterCollection filters,
                     @NotNull Map<Key, Object> additionalData) {
    myDataPack = dataPack;
    myVisibleGraph = graph;
    myCanRequestMore = canRequestMore;
    myFilters = filters;
    myAdditionalData.putAll(additionalData);
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

  @SuppressWarnings("unchecked")
  @Override
  public <T> @Nullable T getUserData(@NotNull Key<T> key) {
    return (T)myAdditionalData.get(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myAdditionalData.put(key, value);
  }

  @NotNull
  public Map<Key, Object> getAdditionalData() {
    return myAdditionalData;
  }

  @Override
  @NonNls
  public String toString() {
    return "VisiblePack{size=" +
           myVisibleGraph.getVisibleCommitCount() +
           ", filters=" +
           myFilters +
           ", canRequestMore=" +
           myCanRequestMore + "}";
  }

  public static class ErrorVisiblePack extends VisiblePack {
    @NotNull private final Throwable myError;

    public ErrorVisiblePack(@NotNull DataPackBase dataPack, @NotNull VcsLogFilterCollection filters, @NotNull Throwable error) {
      super(dataPack, EmptyVisibleGraph.getInstance(), false, filters);
      myError = error;
    }

    @NotNull
    public Throwable getError() {
      return myError;
    }
  }
}
