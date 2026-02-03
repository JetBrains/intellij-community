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

import com.intellij.formatting.engine.testModel.TestBlock
import com.intellij.openapi.util.TextRange


class BlockAttributes(val alignment: Alignment? = null,
                      val wrap: Wrap? = null,
                      val indent: Indent? = null,
                      val spacing: Spacing? = null,
                      val isIncomplete: Boolean = false)

class CompositeTestBlock(startOffset: Int,
                         attributes: BlockAttributes,
                         private val children: List<TestBlockBase>) : TestBlockBase(startOffset, attributes) {
  override fun getSubBlocks() = children
  override fun isLeaf() = false
  override val endOffset = children.last().endOffset

  override fun getSpacing(child1: Block?, child2: Block): Spacing? {
    val right: TestBlockBase = child2 as TestBlockBase
    if (right.getSpacing() != null) {
      return right.getSpacing()
    }
    
    val offset = child2.startOffset
    var firstBlock = child2.firstChild() as? TestBlockBase
    while (firstBlock != null && offset == firstBlock.startOffset) {
      val spacing = firstBlock.getSpacing()
      if (spacing != null) {
        return spacing
      }
      firstBlock = firstBlock.firstChild() as? TestBlockBase
    }
    
    return null
  }
}

class LeafTestBlock(startOffset: Int, attributes: BlockAttributes, val text: String) : TestBlockBase(startOffset, attributes) {
  override fun getSubBlocks() = emptyList<Block>()
  override fun isLeaf() = true
  override val endOffset = startOffset + text.length
}

abstract class TestBlockBase(val startOffset: Int,
                             val attributes: BlockAttributes) : Block {

  override fun getAlignment() = attributes.alignment
  override fun getTextRange() = TextRange(startOffset, endOffset)
  override fun getWrap() = attributes.wrap
  override fun getIndent() = attributes.indent
  override fun getSpacing(child1: Block?, child2: Block) = attributes.spacing

  fun getSpacing() = attributes.spacing
  fun firstChild() = subBlocks.firstOrNull()
  
  override fun getChildAttributes(newChildIndex: Int): ChildAttributes = ChildAttributes(indent, null)
  override fun isIncomplete() = attributes.isIncomplete

  abstract val endOffset: Int

}

class AttributesProvider {

  private val alignments = hashMapOf<Int, Alignment>()
  private val wraps = hashMapOf<String, Wrap>()

  fun extract(text: String): BlockAttributes {
    val attributes: List<String> = text.split(" ").map(String::trim)

    val alignment = getAlignment(attributes)
    val indent = getIndent(attributes)
    val spacing = getSpacing(attributes)
    val wrap = getWrap(attributes)
    val isIncomplete = isIncomplete(attributes)

    return BlockAttributes(alignment, wrap, indent, spacing, isIncomplete)
  }

  private fun isIncomplete(attributes: List<String>) = attributes.contains("incomplete")

  private fun getWrap(attributes: List<String>): Wrap? {
    val wrap = attributes.find { it.startsWith("w_") }?.substring(2) ?: return null
    val matcher = "([a-z]*)([0-9]*)".toPattern().matcher(wrap)
    if (!matcher.matches()) return null

    val type = matcher.group(1)
    val id = matcher.group(2)
    
    return when (type) {
      "normal" -> Wrap.createWrap(WrapType.NORMAL, true)
      "always" -> Wrap.createWrap(WrapType.ALWAYS, true)
      "chop" -> getChopWrapById(id)
      else -> null
    }
  }

  private fun getChopWrapById(id: String): Wrap {
    val wrap = wraps[id]
    if (wrap != null) {
      return wrap
    }
    wraps[id] = Wrap.createWrap(WrapType.CHOP_DOWN_IF_LONG, true)
    return getChopWrapById(id)
  }
  
  private fun getSpacing(attributes: List<String>): Spacing? {
    val spaceProperties = attributes.find { it.startsWith("s_") }?.substring(2)?.split("_") ?: return null

    val minSpaces = spaceProperties.getPropertyValue("min") ?: 1
    val maxSpaces = spaceProperties.getPropertyValue("max") ?: 100
    val minLfs = spaceProperties.getPropertyValue("minlf") ?: 0
    val keepLineBreaks = if (spaceProperties.getPropertyValue("keepLb") == 0) false else true
    
    return Spacing.createSpacing(minSpaces, maxSpaces, minLfs, keepLineBreaks, 100)
  }

  private fun List<String>.getPropertyValue(name: String): Int? {
    return find { it.startsWith(name) }?.substring(name.length)?.toInt()
  }

  private fun getIndent(attributes: List<String>): Indent? {
    val type = attributes.find { it.startsWith("i_") }?.substring(2) ?: return null
    return when (type) {
      "cont" -> Indent.getContinuationIndent()
      "none" -> Indent.getNoneIndent()
      "norm" -> Indent.getNormalIndent()
      "label" -> Indent.getLabelIndent()
      else -> {
        if (type.startsWith("space_")) {
          val spacesCount = type.substringAfter("space_").toInt()
          return Indent.getSpaceIndent(spacesCount)
        }
        return null
      }
    }
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

fun TestBlock.Composite.toFormattingBlock(startOffset: Int,
                                          attributesProvider: AttributesProvider = AttributesProvider()): CompositeTestBlock {
  val childBlocks = mutableListOf<TestBlockBase>()
  var previousStartOffset = startOffset

  children.forEach {
    val block = when (it) {
      is TestBlock.Composite -> {
        val composite = it.toFormattingBlock(previousStartOffset, attributesProvider)
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