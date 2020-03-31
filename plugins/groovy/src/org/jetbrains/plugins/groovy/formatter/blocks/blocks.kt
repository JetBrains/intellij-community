// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.formatter.blocks

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlockGenerator.visibleChildren
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression

fun flattenQualifiedReference(elem: PsiElement): List<ASTNode>? {
 val visibleChildren = visibleChildren(elem.node)
  if (elem is GrReferenceExpression) {
    return visibleChildren
  }
  if (elem is GrMethodCallExpression) {
    val invokedExpression = elem.invokedExpression
    if (invokedExpression is GrQualifiedReference<*>) {
      val invokeNode = invokedExpression.node
      return visibleChildren.flatMap {
        if (it == invokeNode) visibleChildren(invokeNode) else listOf(it)
      }
    }
  }

  return null
}

fun shouldHandleAsSimpleClosure(closure: GrClosableBlock, settings: CommonCodeStyleSettings): Boolean {
  return settings.KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE && !closure.textContains('\n')
}
