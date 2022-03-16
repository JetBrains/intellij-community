// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.formatting

import com.intellij.formatting.Indent
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.groovy.ext.ginq.ast.GinqExpression
import org.jetbrains.plugins.groovy.ext.ginq.ast.getStoredGinq
import org.jetbrains.plugins.groovy.formatter.FormattingContext
import org.jetbrains.plugins.groovy.formatter.GroovyBlockProducer
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlockGenerator

fun produceGinqFormattingBlock(ginq: GinqExpression,
                               context: FormattingContext,
                               node: ASTNode): GinqFragmentContainerBlock {
  val visibleChildren = GroovyBlockGenerator.visibleChildren(node)
  val topBlocks = listOf(ginq.from) + ginq.joins + listOfNotNull(ginq.where, ginq.orderBy, ginq.groupBy, ginq.limit, ginq.select)
  val subBlocks = topBlocks.map { GinqFragmentBlock(it, context) }
  val flattenedChildren = GroovyBlockGenerator.flattenChildren(
    visibleChildren.filter { child -> subBlocks.all { !it.textRange.intersects(child.textRange) } })
  val remainingSubBlocks = flattenedChildren.map {
    context.createBlock(it, Indent.getNoneIndent(), null)
  }
  val allBlocks = (remainingSubBlocks + subBlocks).sortedBy { it.textRange.startOffset }
  return GinqFragmentContainerBlock(allBlocks, context)
}

val GINQ_AWARE_GROOVY_BLOCK_PRODUCER: GroovyBlockProducer = GroovyBlockProducer { node, indent, wrap, context ->
  val ginq = node.psi.getStoredGinq()
  if (ginq != null) {
    produceGinqFormattingBlock(ginq, context, node)
  }
  else {
    GroovyBlockProducer.DEFAULT.generateBlock(node, indent, wrap, context)
  }
}