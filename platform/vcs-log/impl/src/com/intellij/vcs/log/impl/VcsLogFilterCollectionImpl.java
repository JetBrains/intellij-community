// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VcsLogFilterCollectionImpl implements VcsLogFilterCollection {
  @NotNull public static final VcsLogFilterCollection EMPTY = VcsLogFilterObject.collection();
  @NotNull private final Map<FilterKey, VcsLogFilter> myFilters = new TreeMap<>(Comparator.comparing(key -> key.getName()));

  /**
   * @deprecated use {@link VcsLogFilterObject#collection}
   */
  @SuppressWarnings("unused")
  @Deprecated
  public VcsLogFilterCollectionImpl(@Nullable VcsLogBranchFilter branchFilter,
                                    @Nullable VcsLogUserFilter userFilter,
                                    @Nullable VcsLogHashFilter hashFilter,
                                    @Nullable VcsLogDateFilter dateFilter,
                                    @Nullable VcsLogTextFilter textFilter,
                                    @Nullable VcsLogStructureFilter structureFilter,
                                    @Nullable VcsLogRootFilter rootFilter) {
    this(ContainerUtil.skipNulls(Arrays.asList(branchFilter, userFilter, hashFilter, dateFilter, textFilter, structureFilter, rootFilter)));
  }

  protected VcsLogFilterCollectionImpl(@NotNull Collection<? extends VcsLogFilter> filters) {
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
  public String toString() {
    return "filters: (" + myFilters + ")";
  }
}
