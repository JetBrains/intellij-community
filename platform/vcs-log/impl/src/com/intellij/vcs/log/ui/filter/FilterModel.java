/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.filter;

import com.intellij.openapi.util.Computable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogDataPack;
import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

abstract class FilterModel<Filter extends VcsLogFilter> {
  @NotNull private final String myName;
  @NotNull protected final MainVcsLogUiProperties myUiProperties;
  @NotNull private final Computable<VcsLogDataPack> myDataPackProvider;
  @NotNull private final Collection<Runnable> mySetFilterListeners = ContainerUtil.newArrayList();

  @Nullable private Filter myFilter;

  FilterModel(@NotNull String name, @NotNull Computable<VcsLogDataPack> provider, @NotNull MainVcsLogUiProperties uiProperties) {
    myName = name;
    myUiProperties = uiProperties;
    myDataPackProvider = provider;
  }

  void setFilter(@Nullable Filter filter) {
    myFilter = filter;
    saveFilter(filter);
    for (Runnable listener : mySetFilterListeners) {
      listener.run();
    }
  }

  protected void saveFilter(@Nullable Filter filter) {
    myUiProperties.saveFilterValues(myName, filter == null ? null : getFilterValues(filter));
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
    List<String> values = myUiProperties.getFilterValues(myName);
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
