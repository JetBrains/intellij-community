// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.grazie

import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContent.TextDomain.COMMENTS
import com.intellij.grazie.text.TextContent.TextDomain.DOCUMENTATION
import com.intellij.grazie.text.TextContent.TextDomain.LITERALS
import com.intellij.grazie.text.TextContentBuilder
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.utils.Text
import com.intellij.grazie.utils.getNotSoDistantSimilarSiblings
import com.intellij.grazie.utils.replaceBackslashEscapedWhitespace
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.PsiCommentImpl
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens.CODE_BLOCK_TEXT
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens.CODE_SPAN_TEXT
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens.LEADING_ASTERISK
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.isSingleQuoted
import java.util.regex.Pattern

internal class KotlinTextExtractor : TextExtractor() {
  private val kdocBuilder = TextContentBuilder.FromPsi
    .withUnknown { e -> e.elementType == KDocTokens.MARKDOWN_LINK && e.text.startsWith("[") }
    .excluding { e -> e.elementType == KDocTokens.MARKDOWN_LINK && !e.text.startsWith("[") }
    .excluding { e -> val elementType = e.elementType
        elementType == LEADING_ASTERISK || elementType == CODE_BLOCK_TEXT || elementType == CODE_SPAN_TEXT
    }
    .removingIndents(" \t").removingLineSuffixes(" \t")

  public override fun buildTextContents(root: PsiElement, allowedDomains: Set<TextContent.TextDomain>): List<TextContent> {
      if (InjectedLanguageManager.getInstance(root.project).shouldInspectionsBeLenient(root)) {
          return emptyList()
      }

    if (DOCUMENTATION in allowedDomains) {
      if (root is KDocSection) {
        return splitAtMarkdownHeadings(
          kdocBuilder.excluding { e -> e is KDocTag && e != root }.build(root, DOCUMENTATION)?.removeCode()
        )
      }
      if (root is KDocTag && root.name != "author") {
        return splitAtMarkdownHeadings(
          kdocBuilder.excluding { e -> e.elementType == KDocTokens.TAG_NAME }.build(root, DOCUMENTATION)?.removeCode()
        )
      }
    }
    return super.buildTextContents(root, allowedDomains)
  }

  public override fun buildTextContent(root: PsiElement, allowedDomains: Set<TextContent.TextDomain>): TextContent? {
    if (COMMENTS in allowedDomains && root is PsiCommentImpl) {
      val roots = getNotSoDistantSimilarSiblings(root) {
        it == root || root.elementType == KtTokens.EOL_COMMENT && it.elementType == KtTokens.EOL_COMMENT
      }
      return TextContent.joinWithWhitespace('\n', roots.mapNotNull {
          TextContentBuilder.FromPsi.removingIndents(" \t*/").removingLineSuffixes(" \t").build(it, COMMENTS)
      })
    }
    if (LITERALS in allowedDomains && root is KtStringTemplateExpression) {
      // For multiline strings, we want to treat `'|'` as an indentation because it is commonly used with [String.trimMargin].
        val text = TextContentBuilder.FromPsi
            .withUnknown { it is KtStringTemplateEntryWithExpression }
            .removingIndents(" \t|").removingLineSuffixes(" \t")
            .build(root, LITERALS)
        return if (root.isSingleQuoted()) text?.replaceBackslashEscapedWhitespace() else text
    }
    return null
  }

    private val codeFragments = Pattern.compile("(?s)```.+?```|`.+?`")
    private val markdownHeading = Pattern.compile("^[ \\t]*#{1,6}[ \\t]+[^\\n]*?(\\n|$)", Pattern.MULTILINE)

    private fun TextContent.removeCode(): TextContent? =
        excludeRanges(Text.allOccurrences(codeFragments, this).map { TextContent.Exclusion.markUnknown(it) })

    private fun splitAtMarkdownHeadings(content: TextContent?): List<TextContent> {
        if (content == null) return emptyList()
        val matcher = markdownHeading.matcher(content)
        val cuts = buildList {
            while (matcher.find()) {
                if (matcher.start() > 0) add(matcher.start())
                if (matcher.end() < content.length) add(matcher.end())
            }
        }
        if (cuts.isEmpty()) return listOf(content)
        return (listOf(0) + cuts + content.length)
            .zipWithNext()
            .mapNotNull { (start, end) ->
                content.subText(TextRange(start, end))
            }
    }
}
