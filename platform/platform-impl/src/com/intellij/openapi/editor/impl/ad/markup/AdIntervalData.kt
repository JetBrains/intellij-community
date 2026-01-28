// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.markup

import andel.intervals.Interval
import com.intellij.openapi.editor.RangeMarker
import kotlinx.serialization.Serializable


@Serializable
internal data class AdIntervalData(
  val id: Long,
  val start: Int,
  val end: Int,
  val greedyLeft: Boolean,
  val greedyRight: Boolean,
) {

  companion object {
    fun fromInterval(interval: Interval<Long, *>): AdIntervalData {
      return AdIntervalData(
        interval.id,
        interval.from.toInt(),
        interval.to.toInt(),
        interval.greedyLeft,
        interval.greedyRight,
      )
    }

    fun fromRangeMarker(id: Long, rm: RangeMarker): AdIntervalData {
      return AdIntervalData(
        id,
        rm.startOffset,
        rm.endOffset,
        rm.isGreedyToLeft,
        rm.isGreedyToRight,
      )
    }
  }

  fun <T> toInterval(data: T): Interval<Long, T> {
    return Interval(
      id,
      start.toLong(),
      end.toLong(),
      greedyLeft,
      greedyRight,
      data,
    )
  }
}
