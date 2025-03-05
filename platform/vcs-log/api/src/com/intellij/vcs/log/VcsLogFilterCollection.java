// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Encapsulates filters which can be set to VCS Log commits.
 * <p/>
 * All not-null filters are connected with AND, which means that commits should meet all of them. <br/>
 */
public interface VcsLogFilterCollection {
  FilterKey<VcsLogBranchFilter> BRANCH_FILTER = FilterKey.create("branch");
  FilterKey<VcsLogRangeFilter> RANGE_FILTER = FilterKey.create("range");
  FilterKey<VcsLogRevisionFilter> REVISION_FILTER = FilterKey.create("revision");
  FilterKey<VcsLogUserFilter> USER_FILTER = FilterKey.create("user");
  FilterKey<VcsLogHashFilter> HASH_FILTER = FilterKey.create("hash");
  FilterKey<VcsLogDateFilter> DATE_FILTER = FilterKey.create("date");
  FilterKey<VcsLogTextFilter> TEXT_FILTER = FilterKey.create("text");
  FilterKey<VcsLogStructureFilter> STRUCTURE_FILTER = FilterKey.create("structure");
  FilterKey<VcsLogRootFilter> ROOT_FILTER = FilterKey.create("roots");
  FilterKey<VcsLogParentFilter> PARENT_FILTER = FilterKey.create("parent");

  Collection<FilterKey<? extends VcsLogFilter>> STANDARD_KEYS = List.of(BRANCH_FILTER, REVISION_FILTER, RANGE_FILTER,
                                                                        USER_FILTER, HASH_FILTER, DATE_FILTER,
                                                                        TEXT_FILTER, STRUCTURE_FILTER, ROOT_FILTER,
                                                                        PARENT_FILTER);

  @Nullable <T extends VcsLogFilter> T get(@NotNull FilterKey<T> key);

  /**
   * Returns true if there are no filters in this collection.
   */
  default boolean isEmpty() {
    return getFilters().isEmpty();
  }

  @NotNull
  Collection<VcsLogFilter> getFilters();

  default @NotNull @Unmodifiable List<VcsLogDetailsFilter> getDetailsFilters() {
    return ContainerUtil.findAll(getFilters(), VcsLogDetailsFilter.class);
  }

  class FilterKey<T extends VcsLogFilter> {
    private final @NotNull String myName;

    public FilterKey(@NotNull String name) {
      myName = name;
    }

    public @NotNull String getName() {
      return myName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      FilterKey<?> key = (FilterKey<?>)o;
      return Objects.equals(myName, key.myName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myName);
    }

    public static <T extends VcsLogFilter> FilterKey<T> create(@NonNls @NotNull String name) {
      return new FilterKey<>(name);
    }

    @Override
    public @NonNls String toString() {
      return myName + " filter";
    }
  }
}
