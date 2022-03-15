// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.formatting

import com.intellij.formatting.Block
import com.intellij.formatting.Indent
import com.intellij.formatting.Wrap
import com.intellij.formatting.WrapType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqGroupByFragment
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqJoinFragment
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqQueryFragment
import org.jetbrains.plugins.groovy.ext.ginq.ast.isGinqUntransformed
import org.jetbrains.plugins.groovy.formatter.FormattingContext
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlock
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlockGenerator
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyMacroBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class GinqFragmentBlock(val fragment: GinqQueryFragment, context: FormattingContext) :
  GroovyBlock(fragment.keyword.parent.node,
              Indent.getNormalIndent(),
              Wrap.createWrap(WrapType.ALWAYS, true), context) {
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
      val tempBlocks: MutableList<Block> = actualChildren.mapNotNullTo(mutableListOf()) {
        if (it.psi.isGinqUntransformed()) {
          GroovyBlock(it, Indent.getNoneIndent(), null, myContext)
        } else if (GroovyBlockGenerator.canBeCorrectBlock(it)) {
          GroovyMacroBlock(it, context)
        } else {
          null
        }
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

  override fun isLeaf(): Boolean {
    return false
  }
}