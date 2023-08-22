// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.formatting

import com.intellij.formatting.*
import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.ext.ginq.ast.*
import org.jetbrains.plugins.groovy.formatter.FormattingContext
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlock
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlockGenerator
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyMacroBlock
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class GinqFragmentBlock(val fragment: GinqQueryFragment,
                        private val thisAlignment: Alignment?,
                        context: FormattingContext,
                        indent: Indent = Indent.getNormalIndent()) :
  GroovyBlock(fragment.keyword.parent.node,
              indent,
              Wrap.createWrap(WrapType.NORMAL, true),
              context) {

  private val actualChildren = fragment.keyword.parentOfType<GrMethodCall>()!!.run {
    val keyword = this.invokedExpression.asSafely<GrReferenceExpression>()?.referenceNameElement!!
    listOf(keyword.node) + this.node.getChildren(null).takeLastWhile { !PsiTreeUtil.isAncestor(it.psi, keyword, false) }
  }

  override fun getTextRange(): TextRange {
    val maxOffset = maxOf(subBlocks.lastOrNull()?.textRange?.endOffset ?: -1, actualChildren.last().textRange.endOffset)
    val minOffset = minOf(subBlocks.firstOrNull()?.textRange?.startOffset ?: Int.MAX_VALUE, actualChildren.first().startOffset)
    return TextRange(minOffset, maxOffset)
  }

  override fun getSubBlocks(): MutableList<Block> {
    if (mySubBlocks == null) {
      mySubBlocks = computeSubBlocks(fragment)
    }
    return mySubBlocks
  }

  private fun computeSubBlocks(fragment: GinqQueryFragment): MutableList<Block> {
    val tempBlocks: MutableList<Block> = mutableListOf()

    for (child in actualChildren.filter(GroovyBlockGenerator::canBeCorrectBlock)) {
      if (child is LeafPsiElement) {
        tempBlocks.add(GroovyMacroBlock(child, context))
      } else {
        tempBlocks.add(context.createBlock(child, Indent.getNormalIndent(), null))
      }
    }
    if (fragment is GinqJoinFragment && fragment.onCondition != null) {
      val indent = if (context.groovySettings.GINQ_INDENT_ON_CLAUSE) Indent.getNormalIndent() else Indent.getNoneIndent()
      tempBlocks.add(GinqFragmentBlock(fragment.onCondition, null, myContext, indent))
    }
    if (fragment is GinqGroupByFragment && fragment.having != null) {
      val indent = if (context.groovySettings.GINQ_INDENT_HAVING_CLAUSE) Indent.getNormalIndent() else Indent.getNoneIndent()
      tempBlocks.add(GinqFragmentBlock(fragment.having, null, myContext, indent))
    }
    return tempBlocks
  }

  override fun getSpacing(child1: Block?, child2: Block): Spacing? {
    if (child1 == null) {
      return super.getSpacing(null, child2)
    }
    if (child1 is GroovyMacroBlock && child1.node == actualChildren.firstOrNull() &&
        child2 is GroovyBlock && child2.node.elementType == GroovyElementTypes.ARGUMENT_LIST) {
      // select(x) or select (x)
      return if (context.groovySettings.GINQ_SPACE_AFTER_KEYWORD) GinqSpaces.oneStrictSpace else GinqSpaces.emptySpace
    }
    if (child2 is GinqFragmentBlock) {
      // `join ... \n on ...` or `join ... on ...`
      if (child2.fragment is GinqOnFragment) {
        return getUncertainFragmentSpacing(context.groovySettings.GINQ_ON_WRAP_POLICY)
      }
      if (child2.fragment is GinqHavingFragment) {
        return getUncertainFragmentSpacing(context.groovySettings.GINQ_HAVING_WRAP_POLICY)
      }
    }
    return super.getSpacing(child1, child2)
  }

  override fun getAlignment(): Alignment? {
    return thisAlignment ?: super.getAlignment()
  }

  override fun isLeaf(): Boolean {
    return false
  }
}