// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.formatter.blocks

import com.intellij.formatting.*
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.groovy.formatter.FormattingContext
import org.jetbrains.plugins.groovy.formatter.processors.GroovySpacingProcessor

open class SyntheticGroovyBlock(
  private val subBlocks: List<Block>,
  private val wrap: Wrap,
  private val indent: Indent,
  private val childIndent: Indent,
  val context: FormattingContext) : Block {

  init {
    if (subBlocks.isEmpty()) throw IllegalArgumentException("SyntheticGroovyBlock should contain at least one child block")
  }

  override fun getTextRange(): TextRange {
    if (subBlocks.isEmpty()) return TextRange.EMPTY_RANGE
    return TextRange(subBlocks.first().textRange.startOffset, subBlocks.last().textRange.endOffset)
  }

  override fun getSubBlocks(): List<Block> {
    return subBlocks
  }

  override fun getWrap(): Wrap {
    return wrap
  }

  override fun getIndent(): Indent {
    return indent
  }

  override fun getAlignment(): Alignment? {
    return null
  }

  override fun getSpacing(child1: Block?, child2: Block): Spacing? {
    return GroovySpacingProcessor.getSpacing(child1, child2, context)
  }

  override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
    return ChildAttributes(childIndent, null)
  }

  override fun isIncomplete(): Boolean {
    return subBlocks.last().isIncomplete
  }

  override fun isLeaf(): Boolean {
    return false
  }

  fun getFirstChild(): Block {
    return subBlocks.first()
  }

  fun getLastChild(): Block {
    return subBlocks.last()
  }

}
