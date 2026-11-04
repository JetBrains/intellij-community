// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.common

import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.DependantSpacingImpl
import com.intellij.formatting.DependentSpacingRule
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.Wrap
import com.intellij.openapi.util.TextRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class InjectedLanguageBlockWrapperTest {
  @Test
  fun `dependent spacing keeps its rule when the wrapper shifts the dependent ranges`() {
    val rule = DependentSpacingRule(DependentSpacingRule.Trigger.DOES_NOT_HAVE_LINE_FEEDS)
      .registerData(DependentSpacingRule.Anchor.MIN_LINE_FEEDS, 2)
    val first = FakeBlock(TextRange(0, 10))
    val second = FakeBlock(TextRange(10, 20))
    val parent = FakeBlock(TextRange(0, 20), listOf(first, second),
                           DependantSpacingImpl(1, 1, TextRange(5, 10), true, 1, rule))
    val wrapper = InjectedLanguageBlockWrapper(parent, 100, null, null)
    val wrappedChildren = wrapper.subBlocks

    val spacing = assertInstanceOf(DependantSpacingImpl::class.java, wrapper.getSpacing(wrappedChildren[0], wrappedChildren[1]))

    assertEquals(listOf(TextRange(105, 110)), spacing.dependentRegionRanges)
    // The dependent range has no line feeds yet, so the rule requires two line feeds.
    assertEquals(2, spacing.minLineFeeds)
  }

  private class FakeBlock(
    private val range: TextRange,
    private val children: List<Block> = emptyList(),
    private val spacing: Spacing? = null,
  ) : Block {
    override fun getTextRange(): TextRange = range
    override fun getSubBlocks(): List<Block> = children
    override fun getWrap(): Wrap? = null
    override fun getIndent(): Indent? = null
    override fun getAlignment(): Alignment? = null
    override fun getSpacing(child1: Block?, child2: Block): Spacing? = spacing
    override fun getChildAttributes(newChildIndex: Int): ChildAttributes = ChildAttributes(null, null)
    override fun isIncomplete(): Boolean = false
    override fun isLeaf(): Boolean = children.isEmpty()
  }
}
