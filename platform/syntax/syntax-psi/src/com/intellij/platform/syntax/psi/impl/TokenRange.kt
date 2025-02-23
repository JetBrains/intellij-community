// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.psi.tree.IElementType

/**
 * Base class for chameleon tokens
 */
internal abstract class TokenRange : Token() {
  private var tokenStart = 0
  private var tokenEnd = 0
  private var tokenType: IElementType? = null

  override val startOffsetInBuilder: Int
    get() = tokenStart

  override val endOffsetInBuilder: Int
    get() = tokenEnd

  override fun getTokenType(): IElementType? {
    return tokenType
  }

  fun initToken(type: IElementType, parent: CompositeNode, start: Int, end: Int) {
    parentNode = parent
    tokenType = type
    tokenStart = start
    tokenEnd = end
  }
}