// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.vcs.log.VcsLogFilter
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector

abstract class FilterModel<Filter> internal constructor(@JvmField protected val uiProperties: MainVcsLogUiProperties) {
  private val listeners = mutableListOf<Runnable>()

  @JvmField
  protected var _filter: Filter? = null

  open fun setFilter(filter: Filter?) {
    _filter = filter
    saveFilterToProperties(filter)
    notifyFiltersChanged()
  }

  protected fun notifyFiltersChanged() {
    for (listener in listeners) {
      listener.run()
    }
  }

  open fun getFilter(): Filter? {
    if (_filter == null) {
      _filter = getFilterFromProperties()
    }
    return _filter
  }

  protected abstract fun saveFilterToProperties(filter: Filter?)
  protected abstract fun getFilterFromProperties(): Filter?

  fun updateFilterFromProperties() = setFilter(getFilterFromProperties())

  fun addSetFilterListener(runnable: Runnable) {
    listeners.add(runnable)
  }

  abstract class SingleFilterModel<Filter : VcsLogFilter?> internal constructor(private val filterKey: VcsLogFilterCollection.FilterKey<out Filter>,
                                                                                uiProperties: MainVcsLogUiProperties,
                                                                                filters: VcsLogFilterCollection?) :
    FilterModel<Filter?>(uiProperties) {

    init {
      if (filters != null) {
        saveFilterToProperties(filters[filterKey])
      }
    }

    override fun setFilter(filter: Filter?) {
      if (_filter == filter) return

      if (filter != null) {
        VcsLogUsageTriggerCollector.triggerFilterSet(filterKey.name)
      }

      super.setFilter(filter)
    }

    protected abstract fun createFilter(values: List<String>): Filter?
    protected abstract fun getFilterValues(filter: Filter): List<String>

    override fun saveFilterToProperties(filter: Filter?) {
      uiProperties.saveFilterValues(filterKey.name, if (filter == null) null else getFilterValues(filter))
    }

    override fun getFilterFromProperties(): Filter? {
      val values = uiProperties.getFilterValues(filterKey.name) ?: return null
      return createFilter(values)
    }
  }

  abstract class PairFilterModel<Filter1 : VcsLogFilter, Filter2 : VcsLogFilter>
  internal constructor(private val filterKey1: VcsLogFilterCollection.FilterKey<out Filter1>,
                       private val filterKey2: VcsLogFilterCollection.FilterKey<out Filter2>,
                       uiProperties: MainVcsLogUiProperties,
                       filters: VcsLogFilterCollection?) : FilterModel<FilterPair<Filter1, Filter2>>(uiProperties) {
    init {
      if (filters != null) {
        val filter1 = filters[filterKey1]
        val filter2 = filters[filterKey2]
        val filter = if ((filter1 == null && filter2 == null)) null else FilterPair(filter1, filter2)

        saveFilterToProperties(filter)
      }
    }

    override fun setFilter(filter: FilterPair<Filter1, Filter2>?) {
      var newFilter = filter
      if (newFilter != null && newFilter.isEmpty()) newFilter = null

      var anyFiltersDiffers = false
      if (newFilter?.filter1 != _filter?.filter1) {
        if (newFilter?.filter1 != null) {
          VcsLogUsageTriggerCollector.triggerFilterSet(filterKey1.name)
        }
        anyFiltersDiffers = true
      }
      if (newFilter?.filter2 != _filter?.filter2) {
        if (newFilter?.filter2 != null) {
          VcsLogUsageTriggerCollector.triggerFilterSet(filterKey2.name)
        }
        anyFiltersDiffers = true
      }

      if (anyFiltersDiffers) {
        super.setFilter(newFilter)
      }
    }

    override fun saveFilterToProperties(filter: FilterPair<Filter1, Filter2>?) {
      uiProperties.saveFilterValues(filterKey1.name, filter?.filter1?.let { getFilter1Values(it) })
      uiProperties.saveFilterValues(filterKey2.name, filter?.filter2?.let { getFilter2Values(it) })
    }

    override fun getFilterFromProperties(): FilterPair<Filter1, Filter2>? {
      val values1 = uiProperties.getFilterValues(filterKey1.name)
      val filter1 = values1?.let { createFilter1(it) }

      val values2 = uiProperties.getFilterValues(filterKey2.name)
      val filter2 = values2?.let { createFilter2(it) }

      if (filter1 == null && filter2 == null) return null
      return FilterPair(filter1, filter2)
    }

    val filter1: Filter1? get() = getFilter()?.filter1
    val filter2: Filter2? get() = getFilter()?.filter2

    protected abstract fun getFilter1Values(filter1: Filter1): List<String>
    protected abstract fun getFilter2Values(filter2: Filter2): List<String>

    protected abstract fun createFilter1(values: List<String>): Filter1?
    protected abstract fun createFilter2(values: List<String>): Filter2?
  }
}
