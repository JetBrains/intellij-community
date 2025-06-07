// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import kotlin.jvm.JvmName

internal abstract class NodeBase(
  val markerId: Int,
  @get:JvmName( "_getIndex") // class with getStartIndex
  val startIndex: Int,
  val nodeData: NodeData,
  var parent: NodeBase?,
) : Node, com.intellij.lang.SyntaxTreeBuilder.Production {
  var next: NodeBase? = null

  override fun getStartOffset(): Int {
    return nodeData.lexStarts[startIndex] + nodeData.offset
  }

  override fun isCollapsed(): Boolean {
    return nodeData.optionalData.isCollapsed(markerId)
  }

  override fun getStartIndex(): Int {
    return startIndex
  }

  override fun getEndIndex(): Int {
    throw java.lang.UnsupportedOperationException("Shall not be called on this kind of markers")
  }

  abstract fun getLexemeIndex(done: Boolean): Int
}