// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.formatting

import com.intellij.formatting.*
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.groovy.ext.ginq.ast.*
import org.jetbrains.plugins.groovy.formatter.FormattingContext
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlock
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlockGenerator
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyMacroBlock
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class GinqFragmentBlock(val fragment: GinqQueryFragment, context: FormattingContext) :
  GroovyBlock(fragment.keyword.parent.node,
              Indent.getNormalIndent(),
              Wrap.createWrap(WrapType.NORMAL, true), context) {
  private val actualChildren = fragment.keyword.parentOfType<GrMethodCall>()!!.run {
    val keyword = this.invokedExpression.castSafelyTo<GrReferenceExpression>()?.referenceNameElement!!
    listOf(keyword.node) + this.node.getChildren(null).takeLastWhile { !PsiTreeUtil.isAncestor(it.psi, keyword, false) }
  }

  override fun getTextRange(): TextRange {
    val maxOffset = maxOf(subBlocks.lastOrNull()?.textRange?.endOffset ?: -1, actualChildren.last().textRange.endOffset)
    val minOffset = minOf(subBlocks.firstOrNull()?.textRange?.startOffset ?: Int.MAX_VALUE, actualChildren.first().startOffset)
    return TextRange(minOffset, maxOffset)
  }

  override fun getSubBlocks(): MutableList<Block> {
    if (mySubBlocks == null) {
      val tempBlocks: MutableList<Block> = mutableListOf()

      for (child in actualChildren) {
        if (!GroovyBlockGenerator.canBeCorrectBlock(child)) {
          continue
        }
        if (child is LeafPsiElement) {
          tempBlocks.add(GroovyMacroBlock(child, context))
          continue
        }
        tempBlocks.add(context.createBlock(child, Indent.getNormalIndent(), null))
      }
      if (fragment is GinqJoinFragment && fragment.onCondition != null) {
        tempBlocks.add(GinqFragmentBlock(fragment.onCondition, myContext))
      }
      if (fragment is GinqGroupByFragment && fragment.having != null) {
        tempBlocks.add(GinqFragmentBlock(fragment.having, myContext))
      }
      mySubBlocks = tempBlocks
    }
    return mySubBlocks
  }

  override fun getSpacing(child1: Block?, child2: Block): Spacing? {
    if (child1 == null) {
      return super.getSpacing(null, child2)
    }
    if (child1 is GroovyMacroBlock && child1.node == actualChildren.firstOrNull() && child2 is GroovyBlock && child2.node.elementType == GroovyElementTypes.ARGUMENT_LIST) {
      return if (context.groovySettings.GINQ_SPACE_AFTER_KEYWORD) {
        Spacing.createSpacing(1, 1, 0, false, 0)
      } else {
        Spacing.createSpacing(0, 0, 0, false, 0)
      }
    }
    if (child2 is GinqFragmentBlock) {
      // for on or having
      if (child2.fragment is GinqOnFragment) {
        return when (context.groovySettings.GINQ_ON_WRAP_POLICY) {
          CommonCodeStyleSettings.WRAP_ALWAYS -> Spacing.createSpacing(0, 0, 1, true, 2)
          CommonCodeStyleSettings.WRAP_AS_NEEDED -> Spacing.createSpacing(1, 1, 0, true, 2)
          CommonCodeStyleSettings.DO_NOT_WRAP -> Spacing.createSpacing(1, 1, 0, false, 0)
          else -> Spacing.createSpacing(1, 1, 0, true, 0)
        }
      }
      if (child2.fragment is GinqHavingFragment) {
        return when (context.groovySettings.GINQ_HAVING_WRAP_POLICY) {
          CommonCodeStyleSettings.WRAP_ALWAYS -> Spacing.createSpacing(0, 0, 1, true, 2)
          CommonCodeStyleSettings.WRAP_AS_NEEDED -> Spacing.createSpacing(1, 1, 0, true, 2)
          CommonCodeStyleSettings.DO_NOT_WRAP -> Spacing.createSpacing(1, 1, 0, false, 0)
          else -> Spacing.createSpacing(1, 1, 0, true, 0)
        }
      }
    }
    return super.getSpacing(child1, child2)
  }

  override fun isLeaf(): Boolean {
    return false
  }
}