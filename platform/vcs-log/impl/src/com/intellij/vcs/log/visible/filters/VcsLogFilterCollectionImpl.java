// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible.filters;

import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.VcsLogFilterCollection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

class VcsLogFilterCollectionImpl implements VcsLogFilterCollection {
  @NotNull private final Map<FilterKey, VcsLogFilter> myFilters = new TreeMap<>(Comparator.comparing(key -> key.getName()));

  VcsLogFilterCollectionImpl(@NotNull Collection<? extends VcsLogFilter> filters) {
    for (VcsLogFilter filter : filters) {
      myFilters.put(filter.getKey(), filter);
    }
  }

  @Nullable
  @Override
  public <T extends VcsLogFilter> T get(@NotNull FilterKey<T> key) {
    return (T)myFilters.get(key);
  }

  @NotNull
  @Override
  public Collection<VcsLogFilter> getFilters() {
    return myFilters.values();
  }

  @Override
  @NonNls
  public String toString() {
    return "filters: (" + myFilters + ")";
  }
}
