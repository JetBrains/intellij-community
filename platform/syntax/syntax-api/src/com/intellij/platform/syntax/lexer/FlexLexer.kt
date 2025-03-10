// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.lexer

import com.intellij.platform.syntax.SyntaxElementType
import kotlin.jvm.Throws

// A copy of com.intellij.lexer.FlexLexer.Because original advance() method returns IElementTYpe, 
// we had to replace it with a new one that returns SyntaxElementType
interface FlexLexer {
  fun yybegin(state: Int)
  fun yystate(): Int
  fun getTokenStart(): Int
  fun getTokenEnd(): Int

  @Throws(Throwable::class)
  fun advance(): SyntaxElementType?
  fun reset(buf: CharSequence?, start: Int, end: Int, initialState: Int)
}