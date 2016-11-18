/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.formatting

import com.intellij.formatting.*
import com.intellij.formatting.engine.testModel.TestBlock
import com.intellij.openapi.util.TextRange


class BlockAttributes(val alignment: Alignment? = null, val wrap: Wrap? = null, val indent: Indent? = null, val spacing: Spacing? = null)

class CompositeTestBlock(startOffset: Int,
                         attributes: BlockAttributes,
                         private val children: List<TestBlockBase>) : TestBlockBase(startOffset, attributes) 
{
  override fun getSubBlocks() = children
  override fun isLeaf() = false
  override val endOffset = children.last().endOffset
}

class LeafTestBlock(startOffset: Int, attributes: BlockAttributes, val text: String) : TestBlockBase(startOffset, attributes) {
  override fun getSubBlocks() = emptyList<Block>()
  override fun isLeaf() = true
  override val endOffset = startOffset + text.length
}

abstract class TestBlockBase(val startOffset: Int,
                             private val attributes: BlockAttributes) : Block {

  override fun getAlignment() = attributes.alignment
  override fun getTextRange() = TextRange(startOffset, endOffset)
  override fun getWrap() = attributes.wrap
  override fun getIndent() = attributes.indent
  override fun getSpacing(child1: Block?, child2: Block) = attributes.spacing

  override fun getChildAttributes(newChildIndex: Int): ChildAttributes = ChildAttributes.DELEGATE_TO_NEXT_CHILD
  override fun isIncomplete() = false

  abstract val endOffset: Int
  
}

class AttributesProvider {
  
  private val alignments = hashMapOf<Int, Alignment>()
  
  fun extract(text: String): BlockAttributes {
    val attributes: List<String> = text.split(",").map(String::trim)
    val alignment = getAlignment(attributes)
    return BlockAttributes(alignment = alignment)
  }

  private fun getAlignment(attributes: List<String>): Alignment? {
    val index = attributes.find { it.startsWith("a") }?.substring(1)?.toInt()
    if (index == null || index < 0) return null
    
    var alignment = alignments[index]
    if (alignment != null) {
      return alignment
    }
    
    alignment = AlignmentImpl(true, Alignment.Anchor.LEFT)
    alignments[index] = alignment
    return alignment
  }

}

fun TestBlock.Composite.toFormattingBlock(startOffset: Int): CompositeTestBlock {
  val childBlocks = mutableListOf<TestBlockBase>()
  val attributesProvider = AttributesProvider()
  var previousStartOffset = startOffset
  
  children.forEach {
    val block = when (it) {
      is TestBlock.Composite -> {
        val composite = it.toFormattingBlock(previousStartOffset)
        previousStartOffset = composite.endOffset
        composite
      }
      is TestBlock.Leaf -> {
        val attributes = attributesProvider.extract(it.attributes)
        val leaf = LeafTestBlock(previousStartOffset, attributes, it.text)
        previousStartOffset = leaf.endOffset
        leaf
      }
      is TestBlock.Space -> {
        previousStartOffset += it.text.length
        null
      }
    }
    block?.let { childBlocks.add(it) }
  }

  val attributes = attributesProvider.extract(attributes)
  return CompositeTestBlock(startOffset, attributes, childBlocks)
}