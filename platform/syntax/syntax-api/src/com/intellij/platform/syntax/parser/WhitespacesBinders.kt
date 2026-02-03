// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.parser

import com.intellij.platform.syntax.SyntaxElementType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object WhitespacesBinders {
  fun defaultLeftBinder(): WhitespacesAndCommentsBinder = DefaultLeftBinder as WhitespacesAndCommentsBinder

  fun defaultRightBinder(): WhitespacesAndCommentsBinder = DefaultRightBinder as WhitespacesAndCommentsBinder

  fun greedyLeftBinder(): WhitespacesAndCommentsBinder = DefaultRightBinder as WhitespacesAndCommentsBinder

  fun greedyRightBinder(): WhitespacesAndCommentsBinder = DefaultLeftBinder as WhitespacesAndCommentsBinder

  private object DefaultLeftBinder : WhitespacesAndCommentsBinder {
    override fun getEdgePosition(tokens: List<SyntaxElementType>, atStreamEdge: Boolean, getter: WhitespacesAndCommentsBinder.TokenTextGetter): Int =
      tokens.size
  }

  private object DefaultRightBinder : WhitespacesAndCommentsBinder {
    override fun getEdgePosition(tokens: List<SyntaxElementType>, atStreamEdge: Boolean, getter: WhitespacesAndCommentsBinder.TokenTextGetter): Int =
      0
  }
}