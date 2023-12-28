// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogHashFilter
import com.intellij.vcs.log.VcsLogTextFilter
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject

class TextFilterModel internal constructor(properties: MainVcsLogUiProperties,
                                           filters: VcsLogFilterCollection?,
                                           parentDisposable: Disposable) :
  FilterModel.PairFilterModel<VcsLogTextFilter, VcsLogHashFilter>(VcsLogFilterCollection.TEXT_FILTER, VcsLogFilterCollection.HASH_FILTER,
                                                                  properties, filters) {
  init {
    if (filters != null) {
      val textFilter = filters.get(VcsLogFilterCollection.TEXT_FILTER)
      if (textFilter != null) {
        uiProperties.set(MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE, textFilter.matchesCase())
        uiProperties.set(MainVcsLogUiProperties.TEXT_FILTER_REGEX, textFilter.isRegex)
      }
    }
    val listener: VcsLogUiProperties.PropertiesChangeListener = object : VcsLogUiProperties.PropertiesChangeListener {
      override fun <T> onPropertyChanged(property: VcsLogUiProperties.VcsLogUiProperty<T>) {
        if (MainVcsLogUiProperties.TEXT_FILTER_REGEX == property || MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE == property) {
          if (filter1 != null) {
            _filter = getFilterFromProperties()
            notifyFiltersChanged()
          }
        }
      }
    }
    properties.addChangeListener(listener, parentDisposable)
  }

  override fun getFilterFromProperties(): FilterPair<VcsLogTextFilter, VcsLogHashFilter>? {
    val filterPair: FilterPair<VcsLogTextFilter, VcsLogHashFilter>? = super.getFilterFromProperties()
    if (filterPair == null) return null

    var textFilter = filterPair.filter1
    var hashFilter = filterPair.filter2

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

    return FilterPair(textFilter, hashFilter)
  }

  val text: String get() = filter1?.text ?: ""

  override fun getFilter1Values(filter1: VcsLogTextFilter): List<String> = listOf(filter1.text)
  override fun getFilter2Values(filter2: VcsLogHashFilter): List<String> = ArrayList(filter2.hashes)

  override fun createFilter1(values: List<String>): VcsLogTextFilter = createTextFilter(values.first())
  override fun createFilter2(values: List<String>): VcsLogHashFilter = VcsLogFilterObject.fromHashes(values)

  private fun createTextFilter(text: String): VcsLogTextFilter {
    return VcsLogFilterObject.fromPattern(text,
                                          uiProperties.get(MainVcsLogUiProperties.TEXT_FILTER_REGEX),
                                          uiProperties.get(MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE))
  }

  fun setFilterText(text: String) {
    if (text.isBlank()) {
      setFilter(null)
    }
    else {
      val textFilter = createTextFilter(text)
      val hashFilter = VcsLogFilterObject.fromHash(text)
      setFilter(FilterPair(textFilter, hashFilter))
    }
  }

  companion object {
    private val LOG = Logger.getInstance(TextFilterModel::class.java)
  }
}