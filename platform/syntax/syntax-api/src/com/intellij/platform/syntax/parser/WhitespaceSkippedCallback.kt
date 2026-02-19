// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.parser

import com.intellij.platform.syntax.SyntaxElementType
import org.jetbrains.annotations.ApiStatus

/**
 * A hook allowing watch skipped whitespaces
 *
 * @See SyntaxTreeBuilder.setWhitespaceSkippedCallback
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
interface WhitespaceSkippedCallback {
  fun onSkip(type: SyntaxElementType, start: Int, end: Int)
}
