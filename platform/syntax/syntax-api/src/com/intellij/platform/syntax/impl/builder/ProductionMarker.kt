// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

import com.intellij.platform.syntax.parser.SyntaxTreeBuilder

internal abstract class ProductionMarker(
  val markerId: Int,
  val builder: SyntaxTreeBuilderImpl,
) : SyntaxTreeBuilder.Production {

  var startIndex: Int = -1

  final override fun getStartTokenIndex(): Int = startIndex

  open fun dispose() {
    startIndex = -1
  }

  final override fun getStartOffset(): Int =
    builder.lexStart(getStartTokenIndex()) + builder.startOffset

  final override fun isCollapsed(): Boolean =
    builder.myOptionalData.isCollapsed(markerId)

  abstract fun getLexemeIndex(done: Boolean): Int

  abstract fun setLexemeIndex(value: Int, done: Boolean)
}