// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.yaml

import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContent.TextDomain
import com.intellij.grazie.text.TextContentBuilder
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.utils.getNotSoDistantSimilarSiblings
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.elementType
import org.jetbrains.yaml.YAMLSpellcheckerStrategy.JsonSchemaSpellcheckerClientForYaml
import org.jetbrains.yaml.YAMLTokenTypes.COMMENT
import org.jetbrains.yaml.YAMLTokenTypes.EOL
import org.jetbrains.yaml.YAMLTokenTypes.INDENT
import org.jetbrains.yaml.YAMLTokenTypes.SCALAR_KEY
import org.jetbrains.yaml.YAMLTokenTypes.SCALAR_LIST
import org.jetbrains.yaml.YAMLTokenTypes.SCALAR_TEXT
import org.jetbrains.yaml.YAMLTokenTypes.WHITESPACE
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.impl.YAMLAnchorImpl

internal class YamlTextExtractor : TextExtractor() {
  private val commentBuilder = TextContentBuilder.FromPsi.removingIndents(" \t#")

  override fun buildTextContent(root: PsiElement, allowedDomains: MutableSet<TextDomain>): TextContent? {
    if (TextDomain.COMMENTS in allowedDomains && root is PsiComment) {
      val siblings = getNotSoDistantSimilarSiblings(root, TokenSet.create(WHITESPACE, INDENT, EOL)) { it.elementType == COMMENT }
      return TextContent.joinWithWhitespace('\n', siblings.mapNotNull { commentBuilder.build(it, TextDomain.COMMENTS) })
    }
    if (TextDomain.LITERALS in allowedDomains && (root is YAMLScalar || (root.node != null && root.node.elementType == SCALAR_KEY))) {
      if (JsonSchemaSpellcheckerClientForYaml(root).matchesNameFromSchema()) {
        return null
      }
      return TextContentBuilder.FromPsi.excluding { isStealth(it) }.build(root, TextDomain.LITERALS)
    }
    if (TextDomain.LITERALS in allowedDomains && (root is YAMLAnchorImpl && root.parent !is YAMLScalar)) {
      return TextContentBuilder.FromPsi.excluding { isStealth(it) }.build(root, TextDomain.LITERALS)
    }
    return null
  }

  private fun isStealth(element: PsiElement) = when (element.node.elementType) {
    INDENT -> true
    SCALAR_LIST -> element.textLength == 1 && element.textContains('|')
    SCALAR_TEXT -> element.textLength == 1 && element.textContains('>')
    else -> false
  }
}
