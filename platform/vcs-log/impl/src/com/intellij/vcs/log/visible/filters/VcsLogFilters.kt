// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible.filters

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.OpenTHashSet
import com.intellij.vcs.log.VcsLogFilter
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogFilterCollection.FilterKey
import com.intellij.vcs.log.VcsLogTextFilter
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl
import com.intellij.vcs.log.ui.filter.VcsLogTextFilterImpl
import com.intellij.vcs.log.util.VcsLogUtil
import gnu.trove.TObjectHashingStrategy
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

private val LOG = Logger.getInstance("#com.intellij.vcs.log.visible.filters.VcsLogFilters")

object VcsLogFilterObject {
  @JvmStatic
  fun fromPattern(text: String, isRegexpAllowed: Boolean = false, isMatchCase: Boolean = false): VcsLogTextFilter {
    if (isRegexpAllowed && VcsLogUtil.maybeRegexp(text)) {
      try {
        return VcsLogRegexTextFilter(Pattern.compile(text, if (isMatchCase) 0 else Pattern.CASE_INSENSITIVE))
      }
      catch (ignored: PatternSyntaxException) {
      }
    }
    return VcsLogTextFilterImpl(text, isMatchCase)
  }

  @JvmStatic
  fun fromPatternsList(patterns: List<String>, isMatchCase: Boolean = false): VcsLogTextFilter {
    if (patterns.isEmpty()) return fromPattern("", false, isMatchCase)
    if (patterns.size == 1) return fromPattern(patterns.single(), false, isMatchCase)
    return VcsLogMultiplePatternsTextFilter(patterns, isMatchCase)
  }
}

fun createFilterCollection(vararg filters: VcsLogFilter?): VcsLogFilterCollection {
  val filterSet = createFilterSet()
  for (f in filters) {
    if (f != null) {
      if (filterSet.replace(f)) LOG.warn("Two filters with the same key ${f.key} in filter collection. Keeping only ${f}.")
    }
  }
  return MyVcsLogFilterCollectionImpl(filterSet)
}

fun VcsLogFilterCollection.with(filter: VcsLogFilter?): VcsLogFilterCollection {
  if (filter == null) return this

  val filterSet = createFilterSet()
  filterSet.addAll(this.filters)
  filterSet.replace(filter)
  return MyVcsLogFilterCollectionImpl(filterSet)
}

fun VcsLogFilterCollection.without(filterKey: FilterKey<*>): VcsLogFilterCollection {
  val filterSet = createFilterSet()
  this.filters.forEach { if (it.key != filterKey) filterSet.add(it) }
  return MyVcsLogFilterCollectionImpl(filterSet)
}

private fun createFilterSet() = OpenTHashSet<VcsLogFilter>(FilterByKeyHashingStrategy())

private fun <T> OpenTHashSet<T>.replace(element: T): Boolean {
  val isModified = remove(element)
  add(element)
  return isModified
}

private class MyVcsLogFilterCollectionImpl(filterSet: OpenTHashSet<VcsLogFilter>) : VcsLogFilterCollectionImpl(filterSet)

internal class FilterByKeyHashingStrategy : TObjectHashingStrategy<VcsLogFilter> {
  override fun computeHashCode(`object`: VcsLogFilter): Int {
    return `object`.key.hashCode()
  }

  override fun equals(o1: VcsLogFilter, o2: VcsLogFilter): Boolean {
    return o1.key == o2.key
  }
}
