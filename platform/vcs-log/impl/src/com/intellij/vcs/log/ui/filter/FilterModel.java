// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.filter;

import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public abstract class FilterModel<Filter> {
  @NotNull protected final MainVcsLogUiProperties myUiProperties;
  @NotNull private final Collection<Runnable> mySetFilterListeners = new ArrayList<>();

  @Nullable protected Filter myFilter;

  FilterModel(@NotNull MainVcsLogUiProperties uiProperties) {
    myUiProperties = uiProperties;
  }

  public void setFilter(@Nullable Filter filter) {
    myFilter = filter;
    saveFilterToProperties(filter);
    notifyFiltersChanged();
  }

  protected void notifyFiltersChanged() {
    for (Runnable listener : mySetFilterListeners) {
      listener.run();
    }
  }

  @Nullable
  Filter getFilter() {
    if (myFilter == null) {
      myFilter = getFilterFromProperties();
    }
    return myFilter;
  }

  protected abstract void saveFilterToProperties(@Nullable Filter filter);

  @Nullable
  protected abstract Filter getFilterFromProperties();

  void addSetFilterListener(@NotNull Runnable runnable) {
    mySetFilterListeners.add(runnable);
  }

  protected static void triggerFilterSet(@NotNull String name) {
    VcsLogUsageTriggerCollector.triggerFilterSet(name);
  }

  protected static <FilterObject, F> void triggerFilterSet(@Nullable FilterObject filter,
                                                           @NotNull Function<FilterObject, F> getter,
                                                           @NotNull String name) {
    F newFilter = filter == null ? null : getter.apply(filter);
    if (newFilter != null) {
      triggerFilterSet(name);
    }
  }

  protected static <FilterObject, F> boolean filterDiffers(@Nullable FilterObject filter,
                                                           @NotNull Function<FilterObject, F> getter,
                                                           @Nullable FilterObject currentFilter) {
    F oldFilter = currentFilter == null ? null : getter.apply(currentFilter);
    F newFilter = filter == null ? null : getter.apply(filter);
    return !Objects.equals(oldFilter, newFilter);
  }

  public static abstract class SingleFilterModel<Filter extends VcsLogFilter> extends FilterModel<Filter> {
    @NotNull private final VcsLogFilterCollection.FilterKey<? extends Filter> myFilterKey;

    SingleFilterModel(@NotNull VcsLogFilterCollection.FilterKey<? extends Filter> filterKey,
                      @NotNull MainVcsLogUiProperties uiProperties,
                      @Nullable VcsLogFilterCollection filters) {
      super(uiProperties);
      myFilterKey = filterKey;

      if (filters != null) {
        saveFilterToProperties(filters.get(myFilterKey));
      }
    }

    @Override
    public void setFilter(@Nullable Filter filter) {
      if (Objects.equals(myFilter, filter)) return;

      if (filter != null) {
        triggerFilterSet(myFilterKey.getName());
      }

      super.setFilter(filter);
    }

    @Nullable
    protected abstract Filter createFilter(@NotNull List<String> values);

    @NotNull
    protected abstract List<String> getFilterValues(@NotNull Filter filter);

    @Override
    protected void saveFilterToProperties(@Nullable Filter filter) {
      myUiProperties.saveFilterValues(myFilterKey.getName(), filter == null ? null : getFilterValues(filter));
    }

    @Override
    @Nullable
    protected Filter getFilterFromProperties() {
      List<String> values = myUiProperties.getFilterValues(myFilterKey.getName());
      if (values != null) {
        return createFilter(values);
      }
      return null;
    }
  }

  public static abstract class PairFilterModel<Filter1 extends VcsLogFilter, Filter2 extends VcsLogFilter>
    extends FilterModel<FilterPair<Filter1, Filter2>> {
    @NotNull private final VcsLogFilterCollection.FilterKey<? extends Filter1> myFilterKey1;
    @NotNull private final VcsLogFilterCollection.FilterKey<? extends Filter2> myFilterKey2;

    PairFilterModel(@NotNull VcsLogFilterCollection.FilterKey<? extends Filter1> filterKey1,
                    @NotNull VcsLogFilterCollection.FilterKey<? extends Filter2> filterKey2,
                    @NotNull MainVcsLogUiProperties uiProperties,
                    @Nullable VcsLogFilterCollection filters) {
      super(uiProperties);
      myFilterKey1 = filterKey1;
      myFilterKey2 = filterKey2;

      if (filters != null) {
        Filter1 filter1 = filters.get(myFilterKey1);
        Filter2 filter2 = filters.get(myFilterKey2);
        FilterPair<Filter1, Filter2> filter = (filter1 == null && filter2 == null) ? null : new FilterPair<>(filter1, filter2);
        saveFilterToProperties(filter);
      }
    }

    @Override
    public void setFilter(@Nullable FilterPair<Filter1, Filter2> filter) {
      if (filter != null && filter.isEmpty()) filter = null;

      boolean anyFiltersDiffers = false;
      if (filterDiffers(filter, FilterPair::getFilter1, myFilter)) {
        triggerFilterSet(filter, FilterPair::getFilter1, myFilterKey1.getName());
        anyFiltersDiffers = true;
      }
      if (filterDiffers(filter, FilterPair::getFilter2, myFilter)) {
        triggerFilterSet(filter, FilterPair::getFilter2, myFilterKey2.getName());
        anyFiltersDiffers = true;
      }

      if (anyFiltersDiffers) {
        super.setFilter(filter);
      }
    }

    public void updateFilterFromProperties() {
      setFilter(getFilterFromProperties());
    }

    @Override
    protected void saveFilterToProperties(@Nullable FilterPair<Filter1, Filter2> filter) {
      if (filter == null || filter.getFilter1() == null) {
        myUiProperties.saveFilterValues(myFilterKey1.getName(), null);
      }
      else {
        myUiProperties.saveFilterValues(myFilterKey1.getName(), getFilter1Values(filter.getFilter1()));
      }

      if (filter == null || filter.getFilter2() == null) {
        myUiProperties.saveFilterValues(myFilterKey2.getName(), null);
      }
      else {
        myUiProperties.saveFilterValues(myFilterKey2.getName(), getFilter2Values(filter.getFilter2()));
      }
    }

    @Nullable
    @Override
    protected FilterPair<Filter1, Filter2> getFilterFromProperties() {
      List<String> values1 = myUiProperties.getFilterValues(myFilterKey1.getName());
      Filter1 filter1 = null;
      if (values1 != null) {
        filter1 = createFilter1(values1);
      }

      List<String> values2 = myUiProperties.getFilterValues(myFilterKey2.getName());
      Filter2 filter2 = null;
      if (values2 != null) {
        filter2 = createFilter2(values2);
      }

      if (filter1 == null && filter2 == null) return null;
      return new FilterPair<>(filter1, filter2);
    }

    @Nullable
    public Filter1 getFilter1() {
      FilterPair<Filter1, Filter2> filterPair = getFilter();
      if (filterPair == null) return null;
      return filterPair.getFilter1();
    }

    @Nullable
    public Filter2 getFilter2() {
      FilterPair<Filter1, Filter2> filterPair = getFilter();
      if (filterPair == null) return null;
      return filterPair.getFilter2();
    }

    @NotNull
    protected abstract List<String> getFilter1Values(@NotNull Filter1 filter1);

    @NotNull
    protected abstract List<String> getFilter2Values(@NotNull Filter2 filter2);

    @Nullable
    protected abstract Filter1 createFilter1(@NotNull List<String> values);

    @Nullable
    protected abstract Filter2 createFilter2(@NotNull List<String> values);
  }
}
