// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.LighterASTTokenNode
import com.intellij.psi.tree.IElementType

/**
 * A node in light tree
 * Represents a leaf node
 */
internal class SingleLexemeNode : Token(), LighterASTTokenNode {
  var lexemeIndex = 0

  override val startOffsetInBuilder: Int
    get() = this.nodeData.getLexemeStart(lexemeIndex)

  override val endOffsetInBuilder: Int
    get() = this.nodeData.getLexemeStart(lexemeIndex + 1)

  override fun getTokenType(): IElementType {
    return this.nodeData.getLexemeType(lexemeIndex)
  }
}