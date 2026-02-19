// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.ex.lineNumber

import com.intellij.openapi.editor.EditorSettings.LineNumerationType
import com.intellij.openapi.editor.LineNumberConverter

fun getStandardLineNumberConverter(numeration: LineNumerationType): LineNumberConverter {
  return when (numeration) {
    LineNumerationType.RELATIVE -> {
      RelativeLineNumberConverter
    }
    LineNumerationType.HYBRID -> {
      HybridLineNumberConverter
    }
    else -> {
      LineNumberConverter.DEFAULT
    }
  }
}