// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.formatting

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqExpression
import org.jetbrains.plugins.groovy.ext.ginq.ast.getStoredGinq
import org.jetbrains.plugins.groovy.formatter.FormattingContext
import org.jetbrains.plugins.groovy.formatter.GroovyBlockProducer
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlockGenerator
import org.jetbrains.plugins.groovy.formatter.blocks.SyntheticGroovyBlock

internal fun produceGinqFormattingBlock(ginq: GinqExpression,
                                        context: FormattingContext,
                                        node: ASTNode): Block {
  val visibleChildren = GroovyBlockGenerator.visibleChildren(node)
  val topFragments = listOf(ginq.from) + ginq.joins + listOfNotNull(ginq.where, ginq.orderBy, ginq.groupBy, ginq.limit, ginq.select)
  val commonAlignment = Alignment.createAlignment()
  val fragmentBlocks = topFragments.map { GinqFragmentBlock(it, commonAlignment, context) }
  val fragmentContainer = GinqFragmentContainerBlock(fragmentBlocks, context)
  val remainingChildren: List<ASTNode> =
    GroovyBlockGenerator.flattenChildren(
      visibleChildren.filter { child -> fragmentBlocks.all { !it.textRange.intersects(child.textRange) } })
  val remainingSubBlocks = remainingChildren.map {
    context.createBlock(it, Indent.getNoneIndent(), null)
  }
  val allBlocks = (remainingSubBlocks + fragmentContainer).sortedBy { it.textRange.startOffset }

  return SyntheticGroovyBlock(allBlocks, Wrap.createWrap(WrapType.NORMAL, false), Indent.getNoneIndent(), Indent.getNormalIndent(), context)
}

internal val GINQ_AWARE_GROOVY_BLOCK_PRODUCER: GroovyBlockProducer = GroovyBlockProducer { node, indent, wrap, context ->
  val ginq = node.psi.getStoredGinq()
  if (ginq != null) {
    produceGinqFormattingBlock(ginq, context, node)
  }
  else {
    GroovyBlockProducer.DEFAULT.generateBlock(node, indent, wrap, context)
  }
}

internal fun getUncertainFragmentSpacing(policy: Int): Spacing =
  when (policy) {
    CommonCodeStyleSettings.WRAP_ALWAYS -> GinqSpaces.forcedNewline
    CommonCodeStyleSettings.WRAP_AS_NEEDED -> GinqSpaces.laxSpacing
    CommonCodeStyleSettings.DO_NOT_WRAP -> GinqSpaces.oneStrictSpace
    else -> GinqSpaces.laxSpacing
  }

object GinqSpaces {
  val forcedNewline: Spacing = Spacing.createSpacing(0, 0, 1, true, 2)
  val oneStrictSpace: Spacing = Spacing.createSpacing(1, 1, 0, false, 0)
  val emptySpace: Spacing = Spacing.createSpacing(0, 0, 0, false, 0)
  val laxSpacing: Spacing = Spacing.createSpacing(1, 1, 0, true, 2)
}
