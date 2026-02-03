// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.parser

import com.intellij.platform.syntax.SyntaxElementType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.OverrideOnly
fun interface SyntaxElementTypeRemapper {
  /**
   * An external hook to see and alter token types reported by lexer.
   * A lexer might take a delegate implementing this interface.
   * @param source type of element as lexer understood it.
   * @param start start offset of lexeme in text (as lexer.getTokenStart() would return).
   * @param end end offset of lexeme in text (as lexer.getTokenEnd() would return).
   * @param text text being parsed.
   * @return altered (or not) element type.
   */
  fun remap(
    source: SyntaxElementType,
    start: Int,
    end: Int,
    text: CharSequence,
  ): SyntaxElementType
}
