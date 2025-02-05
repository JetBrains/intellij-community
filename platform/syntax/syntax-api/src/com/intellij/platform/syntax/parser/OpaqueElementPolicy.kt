// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.parser

import com.intellij.platform.syntax.SyntaxElementType
import org.jetbrains.annotations.ApiStatus

/**
 * this policy allows overriding the text of an element type
 *
 * @link [com.intellij.lang.TokenWrapper] class
 */
@ApiStatus.Experimental
@ApiStatus.OverrideOnly
fun interface OpaqueElementPolicy {
  /**
   * @return text of opaque element type
   */
  fun getTextOfOpaqueElement(elementType: SyntaxElementType): String?
}