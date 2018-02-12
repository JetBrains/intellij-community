/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.formatter.blocks

import com.intellij.formatting.*
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.groovy.formatter.FormattingContext
import org.jetbrains.plugins.groovy.formatter.processors.GroovySpacingProcessor

open class SyntheticGroovyBlock(
  private val subBlocks: List<Block>,
  private val wrap: Wrap,
  private val indent: Indent,
  val childIndent: Indent,
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

  override fun getSpacing(child1: Block?, child2: Block): Spacing {
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
