// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.utils

import com.intellij.grazie.grammar.Typo
import com.intellij.openapi.util.TextRange

fun Typo.toSelectionRange(): TextRange {
  val end = if (location.pointer?.element!!.textLength >= location.errorRange.endInclusive + 1)
    location.errorRange.endInclusive + 1
  else
    location.errorRange.endInclusive
  return TextRange(location.errorRange.start, end)
}
