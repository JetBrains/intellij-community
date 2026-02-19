// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.lexer

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object StringEscapesTokenTypes {
  val VALID_STRING_ESCAPE_TOKEN: SyntaxElementType = SyntaxElementType("VALID_STRING_ESCAPE_TOKEN")
  val INVALID_CHARACTER_ESCAPE_TOKEN: SyntaxElementType = SyntaxElementType("INVALID_CHARACTER_ESCAPE_TOKEN")
  val INVALID_UNICODE_ESCAPE_TOKEN: SyntaxElementType = SyntaxElementType("INVALID_UNICODE_ESCAPE_TOKEN")

  val STRING_LITERAL_ESCAPES: SyntaxElementTypeSet = syntaxElementTypeSetOf(
    VALID_STRING_ESCAPE_TOKEN,
    INVALID_CHARACTER_ESCAPE_TOKEN,
    INVALID_UNICODE_ESCAPE_TOKEN
  )
}