// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  public static final @NotNull VisiblePack EMPTY =
    new VisiblePack(DataPack.EMPTY, EmptyVisibleGraph.getInstance(), false, VcsLogFilterObject.EMPTY_COLLECTION) {
      @Override
      public String toString() {
        return "EmptyVisiblePack";
      }
    };

  public static final @NotNull Key<Boolean> NO_GRAPH_INFORMATION = Key.create("NO_GRAPH_INFORMATION");

  private final @NotNull DataPackBase myDataPack;
  private final @NotNull VisibleGraph<Integer> myVisibleGraph;
  private final boolean myCanRequestMore;
  private final @NotNull VcsLogFilterCollection myFilters;
  private final @NotNull Map<Key, Object> myAdditionalData = new ConcurrentHashMap<>();

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

  public @NotNull VisibleGraph<Integer> getVisibleGraph() {
    return myVisibleGraph;
  }

  public @NotNull DataPackBase getDataPack() {
    return myDataPack;
  }

  public boolean canRequestMore() {
    return myCanRequestMore;
  }

  @Override
  public @NotNull Map<VirtualFile, VcsLogProvider> getLogProviders() {
    return myDataPack.getLogProviders();
  }

  @Override
  public @NotNull VcsLogRefs getRefs() {
    return myDataPack.getRefsModel();
  }

  public boolean isFull() {
    return myDataPack.isFull();
  }

  @Override
  public @NotNull VcsLogFilterCollection getFilters() {
    return myFilters;
  }

  @Override
  public boolean isEmpty() {
    return getVisibleGraph().getVisibleCommitCount() == 0;
  }

  public @Nullable VirtualFile getRootAtHead(int headCommitIndex) {
    return myDataPack.getRefsModel().rootAtHead(headCommitIndex);
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

  public @NotNull Map<Key, Object> getAdditionalData() {
    return myAdditionalData;
  }

  @Override
  public @NonNls String toString() {
    return "VisiblePack{size=" +
           getVisibleGraph().getVisibleCommitCount() +
           ", filters=" +
           myFilters +
           ", canRequestMore=" +
           myCanRequestMore + "}";
  }

  public static class ErrorVisiblePack extends VisiblePack {
    private final @NotNull Throwable myError;

    public ErrorVisiblePack(@NotNull DataPackBase dataPack, @NotNull VcsLogFilterCollection filters, @NotNull Throwable error) {
      super(dataPack, EmptyVisibleGraph.getInstance(), false, filters);
      myError = error;
    }

    public @NotNull Throwable getError() {
      return myError;
    }
  }
}
