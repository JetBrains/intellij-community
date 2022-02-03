package com.intellij.grazie.ide.language.markdown

import com.intellij.grazie.ide.language.markdown.MarkdownPsiUtils.isMarkdownCodeType
import com.intellij.grazie.ide.language.markdown.MarkdownPsiUtils.isMarkdownLinkType
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContentBuilder
import com.intellij.grazie.text.TextExtractor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes

class MarkdownTextExtractor : TextExtractor() {
  private val toExclude =
    setOf(MarkdownTokenTypes.EMPH, MarkdownTokenTypes.TILDE, MarkdownElementTypes.IMAGE)

  public override fun buildTextContent(root: PsiElement, allowedDomains: Set<TextContent.TextDomain>): TextContent? {
    if (allowedDomains.contains(TextContent.TextDomain.PLAIN_TEXT) &&
        (MarkdownPsiUtils.isHeaderContent(root) || MarkdownPsiUtils.isParagraph(root))) {
      return TextContentBuilder.FromPsi
        .withUnknown { it.node.isMarkdownCodeType() }
        .excluding { e ->
          e.elementType in toExclude ||
          e.firstChild == null && e.parent.node.isMarkdownLinkType() && !isLinkText(e)
        }
        .removingIndents(" \t").removingLineSuffixes(" \t")
        .build(root, TextContent.TextDomain.PLAIN_TEXT)
    }
    return null
  }

  private fun isLinkText(e: PsiElement) =
    (e.elementType == MarkdownTokenTypes.TEXT || e.elementType == MarkdownTokenTypes.GFM_AUTOLINK || e is PsiWhiteSpace) &&
    e.parent.elementType == MarkdownElementTypes.LINK_TEXT
}