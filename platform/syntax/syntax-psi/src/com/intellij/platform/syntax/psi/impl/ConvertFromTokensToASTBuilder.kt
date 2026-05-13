// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.lang.LighterASTNode
import com.intellij.util.diff.DiffTreeChangeBuilder

internal class ConvertFromTokensToASTBuilder(
  rootNode: CompositeNode,
  private val delegate: DiffTreeChangeBuilder<in ASTNode?, in ASTNode?>,
) : DiffTreeChangeBuilder<ASTNode?, LighterASTNode?> {
  private val converter: ASTConverter = ASTConverter(rootNode)

  override fun nodeDeleted(oldParent: ASTNode, oldNode: ASTNode) {
    delegate.nodeDeleted(oldParent, oldNode)
  }

  override fun nodeInserted(oldParent: ASTNode, newNode: LighterASTNode, pos: Int) {
    delegate.nodeInserted(oldParent, converter.convert(newNode as Node), pos)
  }

  override fun nodeReplaced(oldChild: ASTNode, newChild: LighterASTNode) {
    val converted = converter.convert(newChild as Node)
    delegate.nodeReplaced(oldChild, converted)
  }
}