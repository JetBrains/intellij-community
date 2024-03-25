// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.vcs.log.VcsLogFilter
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogHashFilter
import com.intellij.vcs.log.VcsLogTextFilter
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject

class TextFilterModel internal constructor(properties: MainVcsLogUiProperties,
                                           filters: VcsLogFilterCollection?,
                                           parentDisposable: Disposable) :
  FilterModel.MultipleFilterModel(listOf(VcsLogFilterCollection.TEXT_FILTER, VcsLogFilterCollection.HASH_FILTER), properties,
                                  filters) {
  init {
    if (filters != null) {
      val textFilter = filters.get(VcsLogFilterCollection.TEXT_FILTER)
      if (textFilter != null) {
        uiProperties[MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE] = textFilter.matchesCase()
        uiProperties[MainVcsLogUiProperties.TEXT_FILTER_REGEX] = textFilter.isRegex
      }
    }
    val listener: VcsLogUiProperties.PropertiesChangeListener = object : VcsLogUiProperties.PropertiesChangeListener {
      override fun <T> onPropertyChanged(property: VcsLogUiProperties.VcsLogUiProperty<T>) {
        if (MainVcsLogUiProperties.TEXT_FILTER_REGEX == property || MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE == property) {
          if (getFilter(VcsLogFilterCollection.TEXT_FILTER) != null) {
            _filter = getFilterFromProperties()
            notifyFiltersChanged()
          }
        }
      }
    }
    properties.addChangeListener(listener, parentDisposable)
  }

  override fun getFilterFromProperties(): VcsLogFilterCollection? {
    val filterCollection = super.getFilterFromProperties()
    if (filterCollection == null) return null

    var textFilter = filterCollection[VcsLogFilterCollection.TEXT_FILTER]
    var hashFilter = filterCollection[VcsLogFilterCollection.HASH_FILTER]

    // check filters correctness
    if (textFilter != null && textFilter.text.isBlank()) {
      LOG.warn("Saved text filter is empty. Removing.")
      textFilter = null
    }

    if (textFilter != null) {
      val hashFilterFromText = VcsLogFilterObject.fromHash(textFilter.text)
      if (hashFilter != hashFilterFromText) {
        LOG.warn("Saved hash filter " + hashFilter + " is inconsistent with text filter." +
                 " Replacing with " + hashFilterFromText)
        hashFilter = hashFilterFromText
      }
    }
    else if (hashFilter != null && !hashFilter.hashes.isEmpty()) {
      textFilter = createTextFilter(StringUtil.join(hashFilter.hashes, " "))
      LOG.warn("Saved hash filter " + hashFilter +
               " is inconsistent with empty text filter. Using text filter " + textFilter)
    }

    return VcsLogFilterObject.collection(textFilter, hashFilter)
  }

  val text: String get() = getFilter(VcsLogFilterCollection.TEXT_FILTER)?.text ?: ""

  override fun getFilterValues(filter: VcsLogFilter): List<String>? {
    return when (filter) {
      is VcsLogTextFilter -> listOf(filter.text)
      is VcsLogHashFilter -> ArrayList(filter.hashes)
      else -> null
    }
  }

  override fun createFilter(key: VcsLogFilterCollection.FilterKey<*>, values: List<String>): VcsLogFilter? {
    return when (key) {
      VcsLogFilterCollection.TEXT_FILTER -> createTextFilter(values.first())
      VcsLogFilterCollection.HASH_FILTER -> VcsLogFilterObject.fromHashes(values)
      else -> null
    }
  }

  private fun createTextFilter(text: String): VcsLogTextFilter {
    return VcsLogFilterObject.fromPattern(text,
                                          uiProperties[MainVcsLogUiProperties.TEXT_FILTER_REGEX],
                                          uiProperties[MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE])
  }

  fun setFilterText(text: String) {
    if (text.isBlank()) {
      setFilter(null)
    }
    else {
      setFilter(VcsLogFilterObject.collection(createTextFilter(text), VcsLogFilterObject.fromHash(text)))
    }
  }

  companion object {
    private val LOG = Logger.getInstance(TextFilterModel::class.java)
  }
}