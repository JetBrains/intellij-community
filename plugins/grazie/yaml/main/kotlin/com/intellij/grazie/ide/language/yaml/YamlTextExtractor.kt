// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.yaml

import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContentBuilder
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.utils.getNotSoDistantSimilarSiblings
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.elementType
import org.jetbrains.yaml.YAMLTokenTypes.*
import org.jetbrains.yaml.psi.YAMLScalar

class YamlTextExtractor : TextExtractor() {
  private val commentBuilder = TextContentBuilder.FromPsi.removingIndents(" \t#")

  override fun buildTextContent(root: PsiElement, allowedDomains: MutableSet<TextContent.TextDomain>): TextContent? {
    when (root) {
      is PsiComment -> {
        val siblings = getNotSoDistantSimilarSiblings(root, TokenSet.create(WHITESPACE, INDENT, EOL)) { it.elementType == COMMENT }
        return TextContent.joinWithWhitespace('\n', siblings.mapNotNull { commentBuilder.build(it, TextContent.TextDomain.COMMENTS) })
      }
      is YAMLScalar ->
        return TextContentBuilder.FromPsi.excluding { isStealth(it) }.build(root, TextContent.TextDomain.LITERALS)
      else -> return null
    }
  }

  private fun isStealth(element: PsiElement) = when (element.node.elementType) {
    INDENT -> true
    SCALAR_LIST -> element.textLength == 1 && element.textContains('|')
    SCALAR_TEXT -> element.textLength == 1 && element.textContains('>')
    else -> false
  }
}
