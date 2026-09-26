// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.Factory

internal class ASTConverter(private val myRoot: CompositeNode) {
  fun convert(n: Node): ASTNode {
    when (n) {
      is Token -> {
        val token = n
        return token.nodeData.createLeaf(token.getTokenType(), token.startOffsetInBuilder, token.endOffsetInBuilder)
      }
      is ErrorNode -> {
        return Factory.createErrorElement(n.getErrorMessage()!!)
      }
      else -> {
        val compositeNode = n as CompositeNode
        val composite = if (n === myRoot)
          myRoot.nodeData.createRootAST(myRoot) as CompositeElement
        else
          myRoot.nodeData.createComposite(compositeNode, compositeNode.nodeData.astFactory)
        compositeNode.nodeData.bind(compositeNode, composite)
        return composite
      }
    }
  }
}