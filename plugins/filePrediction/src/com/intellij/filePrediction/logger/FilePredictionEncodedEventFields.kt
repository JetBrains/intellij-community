// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.logger

import com.intellij.filePrediction.FilePredictionEventFieldEncoder.encodeBool
import com.intellij.filePrediction.FilePredictionEventFieldEncoder.encodeDouble
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.*

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

internal class CandidateAnonymizedPath : PrimitiveEventField<String?>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#hash}")

  override val name = "file_path"
  override fun addData(fuData: FeatureUsageData, value: String?) {
    fuData.addAnonymizedPath(value)
  }
}