// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.util.Computable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

abstract class FilterModel<Filter extends VcsLogFilter> {
  @NotNull private final VcsLogFilterCollection.FilterKey<? extends Filter> myFilterKey;
  @NotNull protected final MainVcsLogUiProperties myUiProperties;
  @NotNull private final Computable<? extends VcsLogDataPack> myDataPackProvider;
  @NotNull private final Collection<Runnable> mySetFilterListeners = ContainerUtil.newArrayList();

  @Nullable private Filter myFilter;

  FilterModel(@NotNull VcsLogFilterCollection.FilterKey<? extends Filter> filterKey,
              @NotNull Computable<? extends VcsLogDataPack> provider,
              @NotNull MainVcsLogUiProperties uiProperties,
              @Nullable VcsLogFilterCollection filters) {
    myFilterKey = filterKey;
    myUiProperties = uiProperties;
    myDataPackProvider = provider;

    if (filters != null) {
      saveFilter(getFilterFromCollection(filters));
    }
  }

  @Nullable
  protected Filter getFilterFromCollection(@NotNull VcsLogFilterCollection filters) {
    return filters.get(myFilterKey);
  }

  void setFilter(@Nullable Filter filter) {
    myFilter = filter;
    saveFilter(filter);
    for (Runnable listener : mySetFilterListeners) {
      listener.run();
    }
  }

  protected void saveFilter(@Nullable Filter filter) {
    myUiProperties.saveFilterValues(myFilterKey.getName(), filter == null ? null : getFilterValues(filter));
  }

  @Nullable
  Filter getFilter() {
    if (myFilter == null) {
      myFilter = getLastFilter();
    }
    return myFilter;
  }

  @Nullable
  protected abstract Filter createFilter(@NotNull List<String> values);

  @NotNull
  protected abstract List<String> getFilterValues(@NotNull Filter filter);

  @Nullable
  protected Filter getLastFilter() {
    List<String> values = myUiProperties.getFilterValues(myFilterKey.getName());
    if (values != null) {
      return createFilter(values);
    }
    return null;
  }

  @NotNull
  VcsLogDataPack getDataPack() {
    return myDataPackProvider.compute();
  }

  void addSetFilterListener(@NotNull Runnable runnable) {
    mySetFilterListeners.add(runnable);
  }
}
