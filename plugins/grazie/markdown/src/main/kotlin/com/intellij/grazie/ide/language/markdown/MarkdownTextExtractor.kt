package com.intellij.grazie.ide.language.markdown

import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContentBuilder
import com.intellij.grazie.text.TextExtractor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes

class MarkdownTextExtractor : TextExtractor() {
  public override fun buildTextContent(root: PsiElement, allowedDomains: Set<TextContent.TextDomain>): TextContent? {
    if (allowedDomains.contains(TextContent.TextDomain.PLAIN_TEXT) &&
        (MarkdownPsiUtils.isHeaderContent(root) || MarkdownPsiUtils.isParagraph(root))) {
      return TextContentBuilder.FromPsi
        .withUnknown { e ->
          e.firstChild == null &&
          PsiUtilCore.getElementType(e) !== MarkdownTokenTypes.TEXT &&
          MarkdownPsiUtils.isInline(e.parent)
        }
        .build(root, TextContent.TextDomain.PLAIN_TEXT)
    }
    return null
  }
}