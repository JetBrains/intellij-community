package com.intellij.cce.workspace.filter

import com.intellij.tools.ide.metrics.collector.telemetry.SpanFilter as CollectorSpanFilter

data class SpanFilter(val filterType: FilterType, val spanNames: List<String>) {

  fun toCollectorSpanFilter(): CollectorSpanFilter = when (filterType) {
    FilterType.contains -> CollectorSpanFilter.nameContainsAny(spanNames)
    FilterType.equals -> CollectorSpanFilter.nameInList(spanNames)
  }

  enum class FilterType {
    contains,
    equals
  }
}