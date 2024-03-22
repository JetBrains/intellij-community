// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.vcs.log.VcsLogFilter
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.with
import com.intellij.vcs.log.visible.filters.without
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

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
                                                                                initialFilters: VcsLogFilterCollection?) :
    FilterModel<Filter?>(uiProperties) {

    init {
      if (initialFilters != null) {
        saveFilterToProperties(initialFilters[filterKey])
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

  abstract class MultipleFilterModel(private val keys: Collection<VcsLogFilterCollection.FilterKey<*>>,
                                     uiProperties: MainVcsLogUiProperties, initialFilters: VcsLogFilterCollection?) :
    FilterModel<VcsLogFilterCollection>(uiProperties) {

    init {
      if (initialFilters != null) {
        saveFilterToProperties(initialFilters)
      }
    }

    override fun setFilter(filter: VcsLogFilterCollection?) {
      var anyFilterDiffers = false
      for (key in keys) {
        val newFilter = filter?.get(key)
        if (newFilter != _filter?.get(key)) {
          if (newFilter != null) VcsLogUsageTriggerCollector.triggerFilterSet(key.name)
          anyFilterDiffers = true
        }
      }
      if (anyFilterDiffers) {
        super.setFilter(filter)
      }
    }

    override fun saveFilterToProperties(filter: VcsLogFilterCollection?) {
      for (key in keys) {
        val f = filter?.get(key)
        uiProperties.saveFilterValues(key.name, f?.let { getFilterValues(it) })
      }
    }

    override fun getFilterFromProperties(): VcsLogFilterCollection? {
      val filters = keys.mapNotNull { key ->
        uiProperties.getFilterValues(key.name)?.let { createFilter(key, it) }
      }
      if (filters.isEmpty()) return null
      return VcsLogFilterObject.collection(*filters.toTypedArray())
    }

    val filtersList get() = keys.mapNotNull { getFilter()?.get(it) }
    fun <T : VcsLogFilter?> getFilter(key: VcsLogFilterCollection.FilterKey<T>): T? {
      if (!keys.contains(key)) return null
      return getFilter()?.get(key)
    }

    protected abstract fun getFilterValues(filter: VcsLogFilter): List<String>?
    protected abstract fun createFilter(key: VcsLogFilterCollection.FilterKey<*>, values: List<String>): VcsLogFilter?

    protected fun <F : VcsLogFilter?> filterProperty(key: VcsLogFilterCollection.FilterKey<F>, exclusive: Boolean = false): ReadWriteProperty<MultipleFilterModel, F?> {
      return object : ReadWriteProperty<MultipleFilterModel, F?> {
        override fun getValue(thisRef: MultipleFilterModel, property: KProperty<*>): F? = thisRef.getFilter(key)
        override fun setValue(thisRef: MultipleFilterModel, property: KProperty<*>, value: F?) {
          val oldFilter = getFilter()
          val newFilter = if (value == null) {
            oldFilter?.without(key)
          }
          else if (exclusive || oldFilter == null) {
            VcsLogFilterObject.collection(value)
          }
          else {
            oldFilter.with(value)
          }
          setFilter(newFilter)
        }
      }
    }
  }
}
