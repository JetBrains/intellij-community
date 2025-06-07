// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.LighterASTSyntaxTreeBuilderBackedNode
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

internal class CompositeNode(
  markerId: Int,
  private val myType: IElementType,
  startIndex: Int,
  private val myEndIndex: Int,
  data: NodeData,
  parent: CompositeNode?,
) : NodeBase(markerId, startIndex, data, parent), LighterASTSyntaxTreeBuilderBackedNode {

  var myFirstChild: NodeBase? = null
  private var myLastChild: NodeBase? = null

  override fun tokenTextMatches(chars: CharSequence): Boolean {
    check(myFirstChild == null) { "textMatches shouldn't be called on non-empty composite nodes" }
    return chars.isEmpty()
  }

  override fun getEndOffset(): Int {
    return nodeData.lexStarts[myEndIndex] + nodeData.offset
  }

  override fun getEndIndex(): Int {
    return myEndIndex
  }

  override fun getErrorMessage(): String? {
    return if (myType === TokenType.ERROR_ELEMENT) nodeData.optionalData.getErrorMessage(markerId) else null
  }

  override fun getLexemeIndex(done: Boolean): Int {
    return if (done) myEndIndex else startIndex
  }

  fun addChild(node: NodeBase) {
    val lastChild = myLastChild
    if (lastChild == null) {
      myFirstChild = node
    }
    else {
      lastChild.next = node
    }
    myLastChild = node
  }

  override fun getTokenType(): IElementType? {
    return myType
  }

  override fun toString(): String =
    text.toString()

  override fun getText(): CharSequence {
    val originalText = nodeData.text
    val startOffset = startOffset - nodeData.offset
    val endOffset = endOffset - nodeData.offset
    val text = originalText.subSequence(startOffset, endOffset)
    check(text.length == getEndOffset() - getStartOffset())
    return text
  }
}