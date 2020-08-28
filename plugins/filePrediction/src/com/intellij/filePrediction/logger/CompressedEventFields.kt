// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.logger

import com.intellij.internal.statistic.eventLog.events.DoubleEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.IntEventField
import kotlin.math.round

internal class CompressedBooleanEventField(name: String) {
  val field: IntEventField = EventFields.Int(name)

  infix fun with(data: Boolean): EventPair<Int> {
    return field.with(if (data) 1 else 0)
  }
}

internal class CompressedDoubleEventField(name: String) {
  val field: DoubleEventField = EventFields.Double(name)

  infix fun with(data: Double): EventPair<Double> {
    return field.with(roundValue(data))
  }

  private fun roundValue(value: Double): Double {
    if (!value.isFinite()) return -1.0
    return round(value * 100000) / 100000
  }
}

internal class CompressedEnumEventField<T : Enum<*>>(name: String, private val enumClass: Class<T>) {
  val field: IntEventField = EventFields.Int(name)

  infix fun with(data: T): EventPair<Int> {
    return field.with(data.ordinal)
  }
}