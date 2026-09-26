// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.lexer

/**
 * Specifies the state and position of a lexer.
 */
interface LexerPosition {
  /**
   * Returns the offset of the lexer.
   * @return the lexer offset
   */
  val offset: Int

  /**
   * Returns the state of the lexer.
   * @return the lexer state
   */
  val state: Int
}
