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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

class FilterModel<Filter extends VcsLogFilter> {

  @NotNull private final Computable<VcsLogDataPack> myDataPackProvider;
  @NotNull private final Collection<Runnable> mySetFilterListeners = ContainerUtil.newArrayList();

  @Nullable private Filter myFilter;

  FilterModel(@NotNull Computable<VcsLogDataPack> provider) {
    myDataPackProvider = provider;
  }

  void setFilter(@Nullable Filter filter) {
    myFilter = filter;
    for (Runnable listener : mySetFilterListeners) {
      listener.run();
    }
  }

  @Nullable
  Filter getFilter() {
    return myFilter;
  }

  @NotNull
  VcsLogDataPack getDataPack() {
    return myDataPackProvider.compute();
  }

  void addSetFilterListener(@NotNull Runnable runnable) {
    mySetFilterListeners.add(runnable);
  }

}
