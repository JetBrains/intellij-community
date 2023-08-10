// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.formatting

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqExpression
import org.jetbrains.plugins.groovy.ext.ginq.ast.getStoredGinq
import org.jetbrains.plugins.groovy.formatter.FormattingContext
import org.jetbrains.plugins.groovy.formatter.GroovyBlockProducer
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlockGenerator
import org.jetbrains.plugins.groovy.formatter.blocks.SyntheticGroovyBlock
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes

internal fun produceGinqFormattingBlock(ginq: GinqExpression,
                                        context: FormattingContext,
                                        node: ASTNode): Block {
  val visibleChildren = GroovyBlockGenerator.visibleChildren(node)
  val topFragments = listOf(ginq.from) + ginq.joins + listOfNotNull(ginq.where, ginq.orderBy, ginq.groupBy, ginq.limit, ginq.select)
  val commonAlignment = Alignment.createAlignment()
  val fragmentBlocks = topFragments.map { GinqFragmentBlock(it, commonAlignment, context) }
  val ranges = fragmentBlocks.map { it.textRange }
  val minOffset = ranges.minOf { it.startOffset }
  val maxOffset = ranges.maxOf { it.endOffset }
  val remainingChildren: MutableSet<ASTNode> =
    GroovyBlockGenerator.flattenChildren(
      visibleChildren.filter { child -> fragmentBlocks.all { !it.textRange.intersects(child.textRange) } }).toMutableSet()
  for (rootNode in fragmentBlocks.map { it.node }) {
    var currentNode = rootNode.treeParent
    while (currentNode != null && currentNode != node) {
      for (child in GroovyBlockGenerator.visibleChildren(currentNode)) {
        val childRange = child.textRange
        if (ranges.all { !it.intersects(childRange) }) {
          remainingChildren.add(child)
        }
      }
      currentNode = currentNode.treeParent
    }
  }
  val remainingSubBlocks = remainingChildren.map {
    val indent = if (it.elementType == GroovyTokenTypes.mLCURLY || it.elementType == GroovyTokenTypes.mRCURLY) {
      Indent.getNoneIndent()
    } else {
      Indent.getNormalIndent()
    }
    context.createBlock(it, indent, null)
  }
  val inFragment = remainingSubBlocks.filter { TextRange(minOffset, maxOffset).contains(it.textRange) }
  val outOfFragment = remainingSubBlocks - inFragment.toSet()
  val fragmentContainer = GinqFragmentContainerBlock((fragmentBlocks + inFragment).sortedBy { it.textRange.startOffset }, context)
  val allBlocks = (outOfFragment + fragmentContainer).sortedBy { it.textRange.startOffset }

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
    CommonCodeStyleSettings.WRAP_AS_NEEDED -> GinqSpaces.laxSpace
    CommonCodeStyleSettings.DO_NOT_WRAP -> GinqSpaces.oneStrictSpace
    else -> GinqSpaces.laxSpace
  }

object GinqSpaces {
  val forcedNewline: Spacing = Spacing.createSpacing(0, 0, 1, true, 2)
  val oneStrictSpace: Spacing = Spacing.createSpacing(1, 1, 0, false, 0)
  val emptySpace: Spacing = Spacing.createSpacing(0, 0, 0, false, 0)
  val laxSpace: Spacing = Spacing.createSpacing(0, 1, 0, true, 2)
}
