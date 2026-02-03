// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

internal class ErrorNode(
  markerId: Int,
  index: Int,
  data: NodeData,
  parent: CompositeNode,
  private val message: @NlsContexts.DetailedDescription String?,
) : NodeBase(markerId, index, data, parent) {

  override fun getLexemeIndex(done: Boolean): Int {
    require(!done)
    return startIndex
  }

  override fun tokenTextMatches(chars: CharSequence): Boolean = chars.isEmpty()

  override fun getEndOffset(): Int = startOffset

  override fun getEndIndex(): Int = startIndex

  override fun getErrorMessage(): String? = message

  override fun getTokenType(): IElementType = TokenType.ERROR_ELEMENT
}