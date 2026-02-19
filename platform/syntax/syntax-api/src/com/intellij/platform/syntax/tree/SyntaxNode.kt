// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.tree

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxLanguage
import org.jetbrains.annotations.ApiStatus

/**
 * API for a Syntax Node.
 */
@ApiStatus.Experimental
interface SyntaxNode {
  val text: CharSequence

  val type: SyntaxElementType

  val startOffset: Int
  val endOffset: Int

  val errorMessage: String?

  val language: SyntaxLanguage?

  fun parent(): SyntaxNode?

  fun prevSibling(): SyntaxNode?
  fun nextSibling(): SyntaxNode?

  fun firstChild(): SyntaxNode?
  fun lastChild(): SyntaxNode?

  fun childByOffset(offset: Int): SyntaxNode?
}
