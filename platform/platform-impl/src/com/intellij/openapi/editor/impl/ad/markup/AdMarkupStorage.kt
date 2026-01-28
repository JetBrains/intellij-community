// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.markup

import andel.intervals.Interval
import andel.intervals.Intervals
import andel.operation.Operation
import andel.text.Text
import com.intellij.openapi.editor.impl.ad.document.AdTextDocument
import fleet.util.serialization.DataSerializer
import kotlinx.serialization.Serializable


@Serializable(with = AdMarkupStorage.Serializer::class)
internal data class AdMarkupStorage(
  private val document: AdTextDocument,
  private val intervals: Intervals<Long, AdRangeHighlighterData>,
) {
  constructor(text: Text) : this(text, Intervals.keepingCollapsed().empty())
  constructor(text: Text, intervals: Intervals<Long, AdRangeHighlighterData>) : this(AdTextDocument(text), intervals)

  fun query(startOffset: Int, endOffset: Int): Sequence<AdRangeHighlighter> {
    return intervals.query(startOffset.toLong(), endOffset.toLong())
      .map { AdRangeHighlighter.fromInterval(document, it) }
  }

  fun edit(text: Text, operation: Operation): AdMarkupStorage {
    return AdMarkupStorage(text, intervals.edit(operation))
  }

  fun batchUpdate(toAdd: Iterable<AdRangeHighlighter>, toRemove: Iterable<Long>): AdMarkupStorage {
    val intervalsToAdd = toAdd.map { h -> h.toInterval() }
    return copy(intervals = intervals.addIntervals(intervalsToAdd).removeByIds(toRemove))
  }

  internal object Serializer : DataSerializer<AdMarkupStorage, AdMarkupStorageData>(AdMarkupStorageData.serializer()) {
    override fun fromData(data: AdMarkupStorageData): AdMarkupStorage {
      return AdMarkupStorage(
        document = data.toDocument(),
        intervals = Intervals.keepingCollapsed().fromIntervals(intervals = data.toIntervals()),
      )
    }

    override fun toData(value: AdMarkupStorage): AdMarkupStorageData {
      return AdMarkupStorageData(
        text = value.document.textLambda.invoke(),
        highlighters = value.query(0, Int.MAX_VALUE).toList(),
      )
    }
  }

  @Serializable
  internal data class AdMarkupStorageData(
    private val text: Text,
    private val highlighters: List<AdRangeHighlighter>,
  ) {
    fun toDocument(): AdTextDocument = AdTextDocument(text)
    fun toIntervals(): List<Interval<Long, AdRangeHighlighterData>> = highlighters.map { it.toInterval() }
  }
}
