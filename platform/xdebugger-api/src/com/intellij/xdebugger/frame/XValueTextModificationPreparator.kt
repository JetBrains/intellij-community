// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.frame

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface XValueTextModificationPreparator {
  /**
   * Convert given text to string literal used for modification of the value.
   */
  fun convertToStringLiteral(text: String): String
}

@ApiStatus.Experimental
interface XValueTextModificationPreparatorProvider {
  /**
   * Returns a converter used for modification of the given text `value` (usually implementing [XValueTextProvider]).
   */
  @ApiStatus.OverrideOnly
  fun getTextValuePreparator(value: XValue): XValueTextModificationPreparator?
}
