// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.TokenWrapper
import com.intellij.util.text.CharArrayUtil

/**
 * Base class for leaf nodes in light tree
 */
internal abstract class Token : Node {
  lateinit var parentNode: CompositeNode

  override fun tokenTextMatches(chars: CharSequence): Boolean {
    val start = startOffsetInBuilder
    val end = endOffsetInBuilder
    if (end - start != chars.length) return false

    val data = nodeData
    return if (data.textArray != null)
      CharArrayUtil.regionMatches(data.textArray, start, end, chars)
    else
      CharArrayUtil.regionMatches(data.text, start, end, chars)
  }

  override fun getEndOffset(): Int {
    return endOffsetInBuilder + nodeData.offset
  }

  override fun getStartOffset(): Int {
    return startOffsetInBuilder + nodeData.offset
  }

  fun getText(): CharSequence {
    if (getTokenType() is TokenWrapper) {
      return (getTokenType() as TokenWrapper).text
    }

    return nodeData.text.subSequence(startOffsetInBuilder, endOffsetInBuilder)
  }

  val nodeData: NodeData
    get() = parentNode.nodeData

  abstract val startOffsetInBuilder: Int
  abstract val endOffsetInBuilder: Int

  override fun toString(): String {
    return getText().toString()
  }
}