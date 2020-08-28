// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.logger

import com.intellij.filePrediction.logger.FilePredictionEventFieldEncoder.encodeBool
import com.intellij.filePrediction.logger.FilePredictionEventFieldEncoder.encodeDouble
import com.intellij.filePrediction.logger.FilePredictionEventFieldEncoder.encodeFileType
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.text.StringUtil
import kotlin.math.round

internal class CompressedBooleanEventField(name: String) {
  val field: IntEventField = EventFields.Int(name)

  infix fun with(data: Boolean): EventPair<Int> {
    return field.with(encodeBool(data))
  }
}

internal class CompressedDoubleEventField(name: String) {
  val field: DoubleEventField = EventFields.Double(name)

  infix fun with(data: Double): EventPair<Double> {
    return field.with(encodeDouble(data))
  }
}

internal class CompressedEnumEventField<T : Enum<*>>(name: String) {
  val field: IntEventField = EventFields.Int(name)

  infix fun with(data: T): EventPair<Int> {
    return field.with(data.ordinal)
  }
}

internal class CandidateAnonymizedPath: PrimitiveEventField<String?>() {
  override val validationRule: List<String>
    get() = listOf("{regexp#hash}")

  override val name = "file_path"
  override fun addData(fuData: FeatureUsageData, value: String?) {
    fuData.addAnonymizedPath(value)
  }
}

internal class CandidateFeaturesField(name: String) {
  val field: StringEventField = EventFields.StringValidatedByCustomRule(name, "file_features")

  infix fun with(featuresByProvider: Array<Array<Any?>>): EventPair<String?> {
    val result = StringBuilder()
    for ((i, features) in featuresByProvider.withIndex()) {
      if (i > 0) {
        result.append(';')
      }
      appendFeatures(features, result)
    }
    return field.with(result.toString())
  }

  private fun appendFeatures(features: Array<Any?>, result: StringBuilder) {
    for ((i, feature) in features.withIndex()) {
      if (i > 0) {
        result.append(',')
      }
      appendFeature(feature, result)
    }
  }

  private fun appendFeature(feature: Any?, result: StringBuilder) {
    feature?.let {
      when (it) {
        is Double -> {
          result.append(encodeDouble(it))
        }
        is Boolean -> {
          result.append(encodeBool(it))
        }
        is String -> {
          result.append(encodeFileType(it))
        }
        else -> {
          result.append(it.toString())
        }
      }
    }
  }
}

internal object FilePredictionEventFieldEncoder {
  fun encodeDouble(value: Double): Double {
    if (!value.isFinite()) return -1.0
    return round(value * 100000) / 100000
  }

  fun encodeBool(value: Boolean): Int {
    return if (value) 1 else 0
  }

  fun encodeFileType(value: String): String {
    return when (isFileTypeValid(value)) {
      ValidationResultType.ACCEPTED -> value
      ValidationResultType.THIRD_PARTY -> ValidationResultType.THIRD_PARTY.toString()
      else -> "UNKNOWN"
    }
  }

  fun isFileTypeValid(feature: String): ValidationResultType {
    val fileType = FileTypeManager.getInstance().findFileTypeByName(feature)
    if (fileType == null || !StringUtil.equals(fileType.name, feature)) {
      return ValidationResultType.REJECTED
    }

    val isByJB = getPluginInfo(fileType.javaClass).isDevelopedByJetBrains()
    return if (isByJB) ValidationResultType.ACCEPTED else ValidationResultType.THIRD_PARTY
  }
}