// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.prompt

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import kotlin.io.path.exists
import kotlin.io.path.readText

@Suppress("DuplicatedCode")
@ApiStatus.Internal
object AIReviewPromptSupport {
  const val AI_REVIEW_EXECUTE_ACTION_ID: String = "AIReview.AgentWorkbench.ExecuteAction"
  const val DEFAULT_DRAFT_KIND: String = "default"
  const val ISSUES_DRAFT_KIND: String = "issues"

  val DEFAULT_PROMPT: String = """
    You are an AI reviewer that is helping developers review their code.

    Pay close attention to:
     - Possible security vulnerabilities
     - Hard-to-notice bugs
     - Code that may not be intended to be committed, such as local debugging prints or gibberish
     - Notably bad code style
     - Anything else that may have been missed by static analysis tooling

    In your review, you do not need to note every formatting issue. If you find a formatting issue, please note it only once.

    You can report multiple problems, only finish after you have reported all.

    DO NOT HALLUCINATE!
  """.trimIndent()

  val ISSUES_PROMPT: String = """
    Consider the following **bugs** when doing a review:

    <issues-list>

    **IMPORTANT:**
    Ensure that the **bugs** do not exist in the given changes.
  """.trimIndent()

  private const val ISSUES_LIST_PLACEHOLDER: String = "<issues-list>"

  fun loadProjectReviewGuidelines(project: Project): @NlsSafe String {
    val projectRoot = runCatching {
      project.guessProjectDir()?.toNioPath()
    }.getOrNull() ?: return ""

    val guidelineFiles = listOf(".ai/review-guidelines.md", ".ai/review-rules.md", "REVIEW_GUIDELINES.md")
    for (fileName in guidelineFiles) {
      val path = projectRoot.resolve(fileName)
      if (path.exists()) {
        return try {
          path.readText()
        }
        catch (_: Exception) {
          ""
        }
      }
    }
    return ""
  }

  fun renderIssuesPrompt(template: String, issueUrls: List<String>): String {
    val normalizedIssueUrls = normalizeIssueUrls(issueUrls)
    return if (normalizedIssueUrls.isEmpty()) "" else renderIssuesBlock(template, normalizedIssueUrls)
  }

  fun refreshIssueBlocks(text: String, template: String, issueUrls: List<String>): String {
    val normalizedIssueUrls = normalizeIssueUrls(issueUrls)
    val refreshedText = transformIssueBlocks(text, template) { blockText ->
      refreshIssueBlock(blockText, template, normalizedIssueUrls)
    } ?: text
    return cleanupMergedDraft(refreshedText)
  }

  fun containsIssueBlock(text: String, template: String): Boolean {
    return issueBlockRegex(template)?.containsMatchIn(text) == true
  }

  fun replaceIssueBlocks(text: String, template: String, replacement: String): String {
    val replacedText = replaceIssueBlocksOrNull(text, template, replacement) ?: text
    return cleanupMergedDraft(replacedText)
  }

  fun stripIssueBlocks(text: String, template: String): String {
    return replaceIssueBlocks(text, template, "").replace(ISSUES_LIST_PLACEHOLDER, "")
  }

  private fun normalizeIssueUrls(issueUrls: List<String>): LinkedHashSet<String> {
    val normalizedIssueUrls = LinkedHashSet<String>()
    issueUrls.asSequence()
      .map(String::trim)
      .filter(String::isNotEmpty)
      .forEach(normalizedIssueUrls::add)
    return normalizedIssueUrls
  }

  private fun renderIssuesBlock(template: String, issueUrls: Collection<String>): String {
    val issuesList = renderIssuesList(issueUrls)
    return if (template.contains(ISSUES_LIST_PLACEHOLDER)) {
      template.replace(ISSUES_LIST_PLACEHOLDER, issuesList)
    }
    else {
      template
    }
  }

  private fun refreshIssueBlock(text: String, template: String, issueUrls: Collection<String>): String {
    val placeholderIndex = template.indexOf(ISSUES_LIST_PLACEHOLDER)
    if (placeholderIndex < 0) {
      return text
    }

    val heading = template.substring(0, placeholderIndex).trim()
    val importantTail = template.substring(placeholderIndex + ISSUES_LIST_PLACEHOLDER.length).trim()
    if (!text.startsWith(heading) || !text.endsWith(importantTail)) {
      return renderIssuesBlock(template, issueUrls)
    }

    val bodyText = text.substring(heading.length, text.length - importantTail.length)
    val refreshedIssuesList = renderIssuesList(issueUrls)
    val issueListMatch = issueListRegex().find(bodyText)
    val refreshedBodyText = if (issueListMatch != null) {
      bodyText.replaceRange(issueListMatch.range, refreshedIssuesList)
    }
    else {
      bodyText + if (bodyText.endsWith("\n") || bodyText.isEmpty()) refreshedIssuesList else "\n$refreshedIssuesList"
    }
    return heading + refreshedBodyText + importantTail
  }

  private fun replaceIssueBlocksOrNull(text: String, template: String, replacement: String): String? {
    return transformIssueBlocks(text, template) { replacement }
  }

  private fun transformIssueBlocks(
    text: String,
    template: String,
    transform: (String) -> String,
  ): String? {
    val blockRegex = issueBlockRegex(template) ?: return null
    val matches = blockRegex.findAll(text).toList()
    if (matches.isEmpty()) {
      return null
    }

    val result = StringBuilder(text.length)
    var currentIndex = 0
    matches.forEachIndexed { index, match ->
      result.append(text, currentIndex, match.range.first)
      if (index == 0) {
        val transformedText = transform(match.value)
        if (transformedText.isNotBlank()) {
          result.append(transformedText)
        }
      }
      currentIndex = match.range.last + 1
    }
    result.append(text, currentIndex, text.length)
    return result.toString()
  }

  private fun renderIssuesList(issueUrls: Collection<String>): String {
    return issueUrls.joinToString(separator = "\n") { issueUrl ->
      "- $issueUrl"
    }
  }

  private fun issueBlockRegex(template: String): Regex? {
    val placeholderIndex = template.indexOf(ISSUES_LIST_PLACEHOLDER)
    if (placeholderIndex < 0) {
      return null
    }

    val heading = Regex.escape(template.substring(0, placeholderIndex).trim())
    val importantTail = Regex.escape(template.substring(placeholderIndex + ISSUES_LIST_PLACEHOLDER.length).trim())
    return Regex("$heading\\s*.*?\\s*$importantTail", setOf(RegexOption.DOT_MATCHES_ALL))
  }

  private fun issueListRegex(): Regex {
    return Regex("(?m)^[ \\t]*-\\s+https?://\\S+(?:\\R[ \\t]*-\\s+https?://\\S+)*")
  }

  private fun cleanupMergedDraft(text: String): String {
    return text
      .replace(Regex("""\n{3,}"""), "\n\n")
      .trim()
  }
}
