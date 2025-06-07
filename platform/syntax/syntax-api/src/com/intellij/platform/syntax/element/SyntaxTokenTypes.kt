// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.element

import com.intellij.platform.syntax.SyntaxElementType
import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.JvmStatic

/**
 * A set of most used basic token types
 */
@ApiStatus.Experimental
object SyntaxTokenTypes {
  /**
   * Token type for a sequence of whitespace characters.
   */
  @JvmStatic
  val WHITE_SPACE: SyntaxElementType = SyntaxElementType("WHITE_SPACE")

  /**
   * Token type for a character which is not valid in the position where it was encountered,
   * according to the language grammar.
   */
  @JvmStatic
  val BAD_CHARACTER: SyntaxElementType = SyntaxElementType("BAD_CHARACTER")

  @JvmStatic
  val ERROR_ELEMENT: SyntaxElementType = SyntaxElementType("ERROR_ELEMENT")
}