// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.grazie

import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContent.TextDomain.*
import com.intellij.grazie.text.TextContentBuilder
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.utils.getNotSoDistantSimilarSiblings
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.PsiCommentImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal class KotlinTextExtractor : TextExtractor() {
  private val kdocBuilder = TextContentBuilder.FromPsi
    .withUnknown { e -> e.elementType == KDocTokens.MARKDOWN_LINK && e.text.startsWith("[") }
    .excluding { e -> e.elementType == KDocTokens.MARKDOWN_LINK && !e.text.startsWith("[") }
    .excluding { e -> e.elementType == KDocTokens.LEADING_ASTERISK }
    .removingIndents(" \t")

  public override fun buildTextContent(root: PsiElement, allowedDomains: Set<TextContent.TextDomain>): TextContent? {
    if (DOCUMENTATION in allowedDomains) {
      if (root is KDocSection) {
        return kdocBuilder.excluding { e -> e is KDocTag && e != root }.build(root, DOCUMENTATION)
      }
      if (root is KDocTag) {
        return kdocBuilder.excluding { e -> e.elementType == KDocTokens.TAG_NAME }.build(root, DOCUMENTATION)
      }
    }
    if (COMMENTS in allowedDomains && root is PsiCommentImpl) {
      val roots = getNotSoDistantSimilarSiblings(root) {
        it == root || root.elementType == KtTokens.EOL_COMMENT && it.elementType == KtTokens.EOL_COMMENT
      }
      return TextContent.joinWithWhitespace(roots.mapNotNull {
          TextContentBuilder.FromPsi.removingIndents(" \t*/").build(it, COMMENTS)
      })
    }
    if (LITERALS in allowedDomains && root is KtStringTemplateExpression) {
      // For multiline strings, we want to treat `'|'` as an indentation because it is commonly used with [String.trimMargin].
      return TextContentBuilder.FromPsi.removingIndents(" \t|").build(root, LITERALS)
    }
    return null
  }
}