// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.issues

import com.intellij.collaboration.ui.codereview.issues.processIssueIdsMarkdown
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil

object IssuesUtil {
  fun convertMarkdownToHtmlWithIssues(project: Project, markdownText: String): String {
    if (markdownText.isBlank()) return markdownText
    val processedText = processIssueIdsMarkdown(project, markdownText)
    return GitLabUIUtil.convertToHtml(processedText)
  }
}