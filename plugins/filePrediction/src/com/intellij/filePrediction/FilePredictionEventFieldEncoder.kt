// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.text.StringUtil
import kotlin.math.round

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