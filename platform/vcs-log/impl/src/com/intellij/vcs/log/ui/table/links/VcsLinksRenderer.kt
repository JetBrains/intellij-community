// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table.links

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.IssueNavigationConfiguration
import com.intellij.openapi.vcs.IssueNavigationConfiguration.LinkMatch
import com.intellij.openapi.vcs.LinkDescriptor
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleColoredComponent.BrowserLauncherTag
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.text.TextRangeUtil
import com.intellij.vcs.log.CommitId
import org.jetbrains.annotations.ApiStatus

internal class VcsLinksRenderer(project: Project,
                                private val coloredComponent: SimpleColoredComponent,
                                private val commitPrefixLinkRenderer: CommitLinksProvider?) {

  constructor(project: Project, coloredComponent: SimpleColoredComponent) :
    this(project, coloredComponent, project.serviceOrNull<CommitLinksProvider>())

  private val issueNavigationConfiguration = IssueNavigationConfiguration.getInstance(project);

  @Suppress("HardCodedStringLiteral")
  fun appendTextWithLinks(textWithLinks: @NlsSafe String, baseStyle: SimpleTextAttributes, commitId: CommitId?) {
    val issuesLinks = issueNavigationConfiguration.findIssueLinks(textWithLinks)

    val linksToCommits =
      if (commitId != null && commitPrefixLinkRenderer != null) {
        commitPrefixLinkRenderer.getLinks(commitId)
      }
      else {
        emptyList()
      }

    if (issuesLinks.isEmpty() && linksToCommits.isEmpty()) {
      coloredComponent.append(textWithLinks, baseStyle)
      return
    }

    val allMatched =
      (issuesLinks.filterNot { TextRangeUtil.intersectsOneOf(it.range, linksToCommits.map(LinkDescriptor::range)) } + linksToCommits)
        .sortedWith { l1, l2 -> TextRangeUtil.RANGE_COMPARATOR.compare(l1.range, l2.range) }

    val linkStyle = IssueLinkRenderer.getLinkAttributes(baseStyle)

    var processedOffset = 0
    for (link in allMatched) {
      val textRange = link.range

      if (textRange.startOffset > processedOffset) {
        coloredComponent.append(textWithLinks.substring(processedOffset, textRange.startOffset), baseStyle)
      }

      coloredComponent.append(textRange.substring(textWithLinks), linkStyle, link.asTag())

      processedOffset = textRange.endOffset
    }

    if (processedOffset < textWithLinks.length) {
      coloredComponent.append(textWithLinks.substring(processedOffset), baseStyle)
    }
  }

  private fun LinkDescriptor?.asTag(): Any? {
    return when (this) {
      is NavigateToCommit -> this
      is LinkMatch -> BrowserLauncherTag(targetUrl)
      else -> null
    }
  }

  companion object {
    @JvmStatic
    fun isEnabled() = Registry.`is`("vcs.log.render.commit.links", false)
  }
}

@ApiStatus.Internal
class NavigateToCommit(override val range: TextRange, val target: String) : LinkDescriptor
