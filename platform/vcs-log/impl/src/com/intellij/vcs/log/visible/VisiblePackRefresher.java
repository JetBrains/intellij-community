// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible;

import com.intellij.openapi.Disposable;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.graph.PermanentGraph;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
public interface VisiblePackRefresher extends Disposable {

  void addVisiblePackChangeListener(@NotNull VisiblePackChangeListener listener);

  void removeVisiblePackChangeListener(@NotNull VisiblePackChangeListener listener);

  void onRefresh();

  void setValid(boolean validate, boolean refresh);

  void setDataPack(boolean validate, @NotNull DataPack dataPack);

  void onFiltersChange(@NotNull VcsLogFilterCollection filters);

  void onSortTypeChange(@NotNull PermanentGraph.SortType sortType);

  void moreCommitsNeeded(@NotNull Runnable onLoaded);

  boolean isValid();
}
