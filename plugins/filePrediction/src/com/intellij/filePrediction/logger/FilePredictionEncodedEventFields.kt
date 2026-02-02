// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.logger

import com.intellij.filePrediction.FilePredictionEventFieldEncoder.encodeBool
import com.intellij.filePrediction.FilePredictionEventFieldEncoder.encodeDouble
import com.intellij.internal.statistic.eventLog.events.DoubleEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.IntEventField

internal class EncodedBooleanEventField(name: String) {
  val field: IntEventField = EventFields.Int(name)

  infix fun with(data: Boolean): EventPair<Int> {
    return field.with(encodeBool(data))
  }
}

internal class EncodedDoubleEventField(name: String) {
  val field: DoubleEventField = EventFields.Double(name)

  infix fun with(data: Double): EventPair<Double> {
    return field.with(encodeDouble(data))
  }
}

internal class EncodedEnumEventField<T : Enum<*>>(name: String) {
  val field: IntEventField = EventFields.Int(name)

  infix fun with(data: T): EventPair<Int> {
    return field.with(data.ordinal)
  }
}