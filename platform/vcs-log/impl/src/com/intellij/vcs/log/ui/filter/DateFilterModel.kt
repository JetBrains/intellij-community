// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.openapi.diagnostic.Logger
import com.intellij.vcs.log.VcsLogDateFilter
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject

class DateFilterModel(uiProperties: MainVcsLogUiProperties, filters: VcsLogFilterCollection?) :
  FilterModel.SingleFilterModel<VcsLogDateFilter>(VcsLogFilterCollection.DATE_FILTER, uiProperties, filters) {

  override fun createFilter(values: List<String>): VcsLogDateFilter? {
    if (values.size != 2) {
      LOG.warn("Can not create date filter from $values before and after dates are required.")
      return null
    }
    val after = values[0]
    val before = values[1]
    try {
      return VcsLogFilterObject.fromDates(if (after.isEmpty()) 0 else after.toLong(),
                                          if (before.isEmpty()) 0 else before.toLong())
    }
    catch (e: NumberFormatException) {
      LOG.warn("Can not create date filter from $values")
    }
    return null
  }

  override fun getFilterValues(filter: VcsLogDateFilter): List<String> = getDateValues(filter)

  companion object {
    private val LOG = Logger.getInstance(DateFilterModel::class.java)

    fun getDateValues(filter: VcsLogDateFilter): List<String> {
      val after = filter.after
      val before = filter.before
      return listOf(after?.time?.toString() ?: "", before?.time?.toString() ?: "")
    }
  }
}