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
package com.intellij.vcs.log;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  FilterKey<VcsLogRevisionFilter> REVISION_FILTER = FilterKey.create("revision");
  FilterKey<VcsLogUserFilter> USER_FILTER = FilterKey.create("user");
  FilterKey<VcsLogHashFilter> HASH_FILTER = FilterKey.create("hash");
  FilterKey<VcsLogDateFilter> DATE_FILTER = FilterKey.create("date");
  FilterKey<VcsLogTextFilter> TEXT_FILTER = FilterKey.create("text");
  FilterKey<VcsLogStructureFilter> STRUCTURE_FILTER = FilterKey.create("structure");
  FilterKey<VcsLogRootFilter> ROOT_FILTER = FilterKey.create("root");

  @Nullable
  <T extends VcsLogFilter> T get(@NotNull FilterKey<T> key);

  /**
   * Returns true if there are no filters in this collection.
   */
  default boolean isEmpty() {
    return getFilters().isEmpty();
  }

  @NotNull
  Collection<VcsLogFilter> getFilters();

  @NotNull
  default List<VcsLogDetailsFilter> getDetailsFilters() {
    return ContainerUtil.findAll(getFilters(), VcsLogDetailsFilter.class);
  }

  /**
   * To be removed in 2018.2. Use {@link VcsLogFilterCollection#get(FilterKey)} instead.
   */
  @Nullable
  @Deprecated
  default VcsLogBranchFilter getBranchFilter() {
    return get(BRANCH_FILTER);
  }

  /**
   * To be removed in 2018.2. Use {@link VcsLogFilterCollection#get(FilterKey)} instead.
   */
  @Nullable
  @Deprecated
  default VcsLogUserFilter getUserFilter() {
    return get(USER_FILTER);
  }

  /**
   * To be removed in 2018.2. Use {@link VcsLogFilterCollection#get(FilterKey)} instead.
   */
  @Nullable
  @Deprecated
  default VcsLogDateFilter getDateFilter() {
    return get(DATE_FILTER);
  }

  /**
   * To be removed in 2018.2. Use {@link VcsLogFilterCollection#get(FilterKey)} instead.
   */
  @Nullable
  @Deprecated
  default VcsLogTextFilter getTextFilter() {
    return get(TEXT_FILTER);
  }

  /**
   * To be removed in 2018.2. Use {@link VcsLogFilterCollection#get(FilterKey)} instead.
   */
  @Nullable
  @Deprecated
  default VcsLogHashFilter getHashFilter() {
    return get(HASH_FILTER);
  }

  /**
   * To be removed in 2018.2. Use {@link VcsLogFilterCollection#get(FilterKey)} instead.
   */
  @Nullable
  @Deprecated
  default VcsLogStructureFilter getStructureFilter() {
    return get(STRUCTURE_FILTER);
  }

  /**
   * To be removed in 2018.2. Use {@link VcsLogFilterCollection#get(FilterKey)} instead.
   */
  @Nullable
  @Deprecated
  default VcsLogRootFilter getRootFilter() {
    return get(ROOT_FILTER);
  }

  class FilterKey<T extends VcsLogFilter> {
    @NotNull private final String myName;

    public FilterKey(@NotNull String name) {
      myName = name;
    }

    @NotNull
    public String getName() {
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

    public static <T extends VcsLogFilter> FilterKey<T> create(@NotNull String name) {
      return new FilterKey<>(name);
    }

    @Override
    public String toString() {
      return myName + " filter";
    }
  }
}
