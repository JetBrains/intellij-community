package com.intellij.grazie.ide.language.markdown

import com.intellij.grazie.ide.language.markdown.MarkdownPsiUtils.isMarkdownCodeType
import com.intellij.grazie.ide.language.markdown.MarkdownPsiUtils.isMarkdownLinkType
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContentBuilder
import com.intellij.grazie.text.TextExtractor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes

class MarkdownTextExtractor : TextExtractor() {
  public override fun buildTextContent(root: PsiElement, allowedDomains: Set<TextContent.TextDomain>): TextContent? {
    if (allowedDomains.contains(TextContent.TextDomain.PLAIN_TEXT) &&
        (MarkdownPsiUtils.isHeaderContent(root) || MarkdownPsiUtils.isParagraph(root))) {
      return TextContentBuilder.FromPsi
        .withUnknown { e ->
          e.node.isMarkdownCodeType() ||
          e.firstChild == null && e.parent.node.isMarkdownLinkType() && !isLinkText(e)
        }
        .build(root, TextContent.TextDomain.PLAIN_TEXT)
    }
    return null
  }

  private fun isLinkText(e: PsiElement) =
    e.elementType == MarkdownTokenTypes.TEXT && e.parent.elementType == MarkdownElementTypes.LINK_TEXT
}