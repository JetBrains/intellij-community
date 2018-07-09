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
package com.intellij.vcs.log.impl;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OpenTHashSet;
import com.intellij.vcs.log.*;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VcsLogFilterCollectionImpl implements VcsLogFilterCollection {
  @NotNull public static final VcsLogFilterCollection EMPTY = new VcsLogFilterCollectionBuilder().build();
  @NotNull private final Map<FilterKey, VcsLogFilter> myFilters = new TreeMap<>(Comparator.comparing(key -> key.getName()));

  public VcsLogFilterCollectionImpl(@Nullable VcsLogBranchFilter branchFilter,
                                    @Nullable VcsLogUserFilter userFilter,
                                    @Nullable VcsLogHashFilter hashFilter,
                                    @Nullable VcsLogDateFilter dateFilter,
                                    @Nullable VcsLogTextFilter textFilter,
                                    @Nullable VcsLogStructureFilter structureFilter,
                                    @Nullable VcsLogRootFilter rootFilter) {
    this(ContainerUtil.skipNulls(Arrays.asList(branchFilter, userFilter, hashFilter, dateFilter, textFilter, structureFilter, rootFilter)));
  }

  public VcsLogFilterCollectionImpl(@NotNull Collection<VcsLogFilter> filters) {
    for (VcsLogFilter filter: filters) {
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

  public static class VcsLogFilterCollectionBuilder {
    @NotNull private final Collection<VcsLogFilter> myFilters = new OpenTHashSet<>(new FilterByKeyHashingStrategy());

    public VcsLogFilterCollectionBuilder() {
    }

    public VcsLogFilterCollectionBuilder(@NotNull VcsLogFilterCollection filterCollection) {
      myFilters.addAll(filterCollection.getFilters());
    }

    public VcsLogFilterCollectionBuilder(VcsLogFilter... filters) {
      myFilters.addAll(ContainerUtil.skipNulls(Arrays.asList(filters)));
    }

    @NotNull
    public VcsLogFilterCollectionBuilder with(@Nullable VcsLogFilter filter) {
      if (filter != null) {
        myFilters.remove(filter); // need to replace
        myFilters.add(filter);
      }
      return this;
    }

    @NotNull
    public <T extends VcsLogFilter> VcsLogFilterCollectionBuilder without(@NotNull FilterKey<T> key) {
      myFilters.removeIf(filter -> filter.getKey().equals(key));
      return this;
    }

    @NotNull
    public VcsLogFilterCollection build() {
      return new VcsLogFilterCollectionImpl(myFilters);
    }

    private static class FilterByKeyHashingStrategy implements TObjectHashingStrategy<VcsLogFilter> {
      @Override
      public int computeHashCode(@NotNull VcsLogFilter object) {
        return object.getKey().hashCode();
      }

      @Override
      public boolean equals(@NotNull VcsLogFilter o1, @NotNull VcsLogFilter o2) {
        return o1.getKey().equals(o2.getKey());
      }
    }
  }
}
