// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.TokenSeparatorGenerator
import com.intellij.psi.PsiManager
import com.intellij.psi.TokenType
import com.intellij.psi.impl.source.tree.Factory

class EditorConfigTokenSeparatorGenerator : TokenSeparatorGenerator {
  override fun generateWhitespaceBetweenTokens(left: ASTNode, right: ASTNode): ASTNode? {
    val manager = right.treeParent.psi.manager
    if (left.elementType == EditorConfigElementTypes.LINE_COMMENT) {
      return createLineBreak(manager)
    }

    return null
  }

  private fun createLineBreak(manager: PsiManager) =
    createWhitespace(manager, "\n")

  private fun createWhitespace(manager: PsiManager, text: String) =
    Factory.createSingleLeafElement(TokenType.WHITE_SPACE, text, 0, text.length, null, manager)
}
