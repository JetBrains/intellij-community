// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.editorconfig.common.formatting

import com.intellij.editorconfig.common.syntax.psi.EditorConfigElementTypes
import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.formatter.common.AbstractBlock

class EditorConfigFormattingBlock(
  node: ASTNode,
  private val builder: SpacingBuilder,
  private val shouldAlignSeparators: Boolean,
  private val separatorAlignment: Alignment? = null
) : AbstractBlock(node, Wrap.createWrap(WrapType.NONE, false), Alignment.createAlignment()) {

  override fun buildChildren(): List<Block> {
    val result = arrayListOf<Block>()

    val newSeparatorAlignment =
      if (isSection) Alignment.createAlignment(true, Alignment.Anchor.LEFT)
      else separatorAlignment

    var child = myNode.firstChildNode
    while (child != null) {
      if (child.elementType != TokenType.WHITE_SPACE) {
        val block = EditorConfigFormattingBlock(child, builder, shouldAlignSeparators, newSeparatorAlignment)
        result.add(block)
      }

      child = child.treeNext
    }
    return result
  }

  override fun getIndent(): Indent = Indent.getNoneIndent()

  override fun getSpacing(child1: Block?, child2: Block) : Spacing? {
    return when {
      isUnderHeader() -> Spacing.getReadOnlySpacing()
      else -> builder.getSpacing(this, child1, child2)
    }
  }

  private fun isUnderHeader() : Boolean {
    var parent = node.treeParent
    while (parent != null && parent.psi !is PsiFile) {
      if (parent.elementType == EditorConfigElementTypes.HEADER) {
        return true
      }
      parent = parent.treeParent
    }
    return false
  }

  override fun isLeaf(): Boolean = myNode.firstChildNode === null

  override fun getAlignment(): Alignment? =
    if (isSeparator && shouldAlignSeparators) separatorAlignment
    else myAlignment

  private val isSeparator
    get() = node.elementType == EditorConfigElementTypes.SEPARATOR

  private val isSection
    get() = node.elementType == EditorConfigElementTypes.SECTION
}
