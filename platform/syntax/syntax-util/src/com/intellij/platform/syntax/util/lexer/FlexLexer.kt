// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.lexer

import com.intellij.platform.syntax.SyntaxElementType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface FlexLexer {
  fun yybegin(newState: Int)
  fun yystate(): Int
  fun getTokenStart(): Int
  fun getTokenEnd(): Int

  fun advance(): SyntaxElementType?
  fun reset(buf: CharSequence, start: Int, end: Int, initialState: Int)
}