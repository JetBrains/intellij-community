// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.tree

import com.intellij.platform.syntax.SyntaxElementType
import org.jetbrains.annotations.ApiStatus

/**
 * API for a Syntax Tree.
 *
 * The tree is passed to [com.intellij.platform.syntax.LazyParser] as the context for parsing.
 */
@ApiStatus.Experimental
interface SyntaxTree {
  val text: CharSequence
  val root: SyntaxNode
}

/**
 * API for a Syntax Node.
 *
 */
@ApiStatus.Experimental
interface SyntaxNode {
  val text: CharSequence

  val type: SyntaxElementType

  val startOffset: Int
  val endOffset: Int

  val parent: SyntaxNode?

  val prevSibling: SyntaxNode?
  val nextSibling: SyntaxNode?

  val firstChild: SyntaxNode?
  val lastChild: SyntaxNode?

  val errorMessage: String?
}
