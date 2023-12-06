// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

/**
 * @see VcsLogFilterObject#collection(VcsLogFilter...)
 */
class VcsLogFilterCollectionImpl implements VcsLogFilterCollection {
  private final @NotNull Map<FilterKey, VcsLogFilter> myFilters = new TreeMap<>(Comparator.comparing(key -> key.getName()));

  VcsLogFilterCollectionImpl(@NotNull Collection<? extends VcsLogFilter> filters) {
    for (VcsLogFilter filter : filters) {
      myFilters.put(filter.getKey(), filter);
    }
  }

  @Override
  public @Nullable <T extends VcsLogFilter> T get(@NotNull FilterKey<T> key) {
    return (T)myFilters.get(key);
  }

  @Override
  public @NotNull Collection<VcsLogFilter> getFilters() {
    return myFilters.values();
  }

  @Override
  public @NonNls String toString() {
    return "filters: (" + myFilters + ")";
  }
}
