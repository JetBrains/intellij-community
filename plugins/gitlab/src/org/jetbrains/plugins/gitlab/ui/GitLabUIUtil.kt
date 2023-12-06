// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui

import com.intellij.collaboration.ui.codereview.issues.processIssueIdsMarkdown
import com.intellij.markdown.utils.CodeFenceSyntaxHighlighterGeneratingProvider
import com.intellij.markdown.utils.MarkdownToHtmlConverter
import com.intellij.markdown.utils.lang.CodeBlockHtmlSyntaxHighlighter
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.parser.LinkMap
import org.jetbrains.annotations.NonNls
import java.net.URI

object GitLabUIUtil {
  internal fun convertToHtml(project: Project, markdownSource: @NonNls String): @NlsSafe String {
    if (markdownSource.isBlank()) return markdownSource
    val text = processIssueIdsMarkdown(project, markdownSource).replace("\r", "") // TODO: fix bug with CRLF line endings from markdown library
    val flavourDescriptor = GitLabFlavourDescriptor(CodeBlockHtmlSyntaxHighlighter(project))

    return MarkdownToHtmlConverter(flavourDescriptor).convertMarkdownToHtml(text, null)
  }

  private class GitLabFlavourDescriptor(
    private val htmlSyntaxHighlighter: HtmlSyntaxHighlighter
  ) : GFMFlavourDescriptor() {
    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
      val parentProviders = super.createHtmlGeneratingProviders(linkMap, baseURI).toMutableMap()
      parentProviders[MarkdownElementTypes.CODE_FENCE] = CodeFenceSyntaxHighlighterGeneratingProvider(htmlSyntaxHighlighter)
      return parentProviders
    }
  }
}