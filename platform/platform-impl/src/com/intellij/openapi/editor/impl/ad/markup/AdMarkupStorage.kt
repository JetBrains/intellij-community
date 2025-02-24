// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.markup

import andel.intervals.Interval
import andel.intervals.Intervals
import andel.operation.Operation
import fleet.util.serialization.DataSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Experimental


@Experimental
@Serializable(with = AdMarkupStorageSerializer::class)
internal data class AdMarkupStorage(private val intervals: Intervals<Long, AdRangeHighlighterData>) {

  companion object {
    fun empty(): AdMarkupStorage = AdMarkupStorage(Intervals.keepingCollapsed().empty())
  }

  fun query(startOffset: Int, endOffset: Int): Sequence<AdRangeHighlighter> {
    return intervals.query(startOffset.toLong(), endOffset.toLong())
      .map { AdRangeHighlighter.fromInterval(it) } // TODO: allocations on each query
  }

  fun edit(operation: Operation): AdMarkupStorage {
    return copy(intervals = intervals.edit(operation))
  }

  fun batchUpdate(
    highlightersToAdd: Iterable<AdRangeHighlighter>,
    highlightersToRemove: Iterable<Long>,
  ): AdMarkupStorage {
    val intervalsToAdd = highlightersToAdd.map { h -> h.toInterval() }
    return copy(intervals = intervals.addIntervals(intervalsToAdd).removeByIds(highlightersToRemove))
  }
}

private object AdMarkupStorageSerializer : DataSerializer<AdMarkupStorage, AdMarkupStorageData>(AdMarkupStorageData.serializer()) {
  override fun fromData(data: AdMarkupStorageData): AdMarkupStorage {
    return AdMarkupStorage(Intervals.keepingCollapsed().fromIntervals(intervals = data.toIntervals()))
  }

  override fun toData(value: AdMarkupStorage): AdMarkupStorageData {
    return AdMarkupStorageData(highlighters = value.query(0, Int.MAX_VALUE).toList())
  }
}

@Serializable
private data class AdMarkupStorageData(private val highlighters: List<AdRangeHighlighter>) {
  fun toIntervals(): List<Interval<Long, AdRangeHighlighterData>> = highlighters.map { it.toInterval() }
}
