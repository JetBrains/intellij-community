// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language.markdown

import com.intellij.grazie.utils.hasType
import com.intellij.grazie.utils.noParentOfTypes
import com.intellij.grazie.utils.parents
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes

@Suppress("MemberVisibilityCanBePrivate")
internal object MarkdownPsiUtils {
  internal fun ASTNode.isMarkdownLinkType() = when (elementType) {
    MarkdownElementTypes.LINK_DEFINITION,
    MarkdownElementTypes.LINK_LABEL,
    MarkdownElementTypes.LINK_DESTINATION,
    MarkdownElementTypes.LINK_TITLE,
    MarkdownElementTypes.LINK_TEXT,
    MarkdownElementTypes.LINK_COMMENT,
    MarkdownElementTypes.FULL_REFERENCE_LINK,
    MarkdownElementTypes.SHORT_REFERENCE_LINK,
    MarkdownElementTypes.AUTOLINK,
    MarkdownElementTypes.INLINE_LINK -> true
    else -> false
  }

  internal fun ASTNode.isMarkdownCodeType() = when (elementType) {
    MarkdownElementTypes.CODE_FENCE,
    MarkdownElementTypes.CODE_BLOCK,
    MarkdownElementTypes.CODE_SPAN -> true
    else -> false
  }

  fun isParagraph(element: PsiElement) = element.node?.hasType(MarkdownElementTypes.PARAGRAPH) ?: false
  fun isHeaderContent(element: PsiElement) = element.node?.hasType(MarkdownTokenTypes.ATX_CONTENT, MarkdownTokenTypes.SETEXT_CONTENT)
                                             ?: false

  fun isOuterListItem(element: PsiElement) = element.node?.hasType(MarkdownElementTypes.LIST_ITEM) ?: false
                                             && element.node?.noParentOfTypes(TokenSet.create(MarkdownElementTypes.LIST_ITEM)) ?: false

  fun isInOuterListItem(element: PsiElement) = element.parents().any { isOuterListItem(it) }
}
