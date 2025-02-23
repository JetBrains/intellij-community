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
data class AdMarkupStorage(val intervals: Intervals<Long, AdRangeHighlighterData>) {

  companion object {
    fun empty(): AdMarkupStorage = AdMarkupStorage(Intervals.keepingCollapsed().empty())
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

  override fun fromData(storageData: AdMarkupStorageData): AdMarkupStorage {
    val intervals = storageData.toIntervals()
    return AdMarkupStorage(Intervals.keepingCollapsed().fromIntervals(intervals))
  }

  override fun toData(storage: AdMarkupStorage): AdMarkupStorageData {
    return AdMarkupStorageData(
      storage.intervals.query(0, Long.MAX_VALUE).map { i ->
        AdRangeHighlighter.fromInterval(i)
      }.toList()
    )
  }
}

@Serializable
private data class AdMarkupStorageData(private val highlighters: List<AdRangeHighlighter>) {
  fun toIntervals(): Iterable<Interval<Long, AdRangeHighlighterData>> {
    return highlighters.map { adHighlighter ->
      adHighlighter.toInterval()
    }
  }
}
