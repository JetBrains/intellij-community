// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.prompt

import com.intellij.agent.workbench.ai.review.AIReviewBundle
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteExtension
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteExtensionContext
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteInitialPrompt
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.agent.workbench.prompt.core.array
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.agent.workbench.prompt.core.string
import com.intellij.openapi.project.Project

internal class AIReviewPaletteExtension : AgentPromptPaletteExtension {
  override fun matches(contextItems: List<AgentPromptContextItem>): Boolean {
    return contextItems.any(::isReviewContext)
  }

  override fun getTabTitle(): String = AIReviewBundle.message("popup.palette.tab.ai.review")

  override fun getInitialPrompt(project: Project): AgentPromptPaletteInitialPrompt {
    val kind = when {
      AIReviewPromptSupport.loadProjectReviewGuidelines(project).isNotBlank() -> AIReviewPromptSupport.DEFAULT_DRAFT_KIND
      collectIssueUrls(AgentPromptPaletteExtensionContext.getContextItems(project)).isNotEmpty() -> AIReviewPromptSupport.ISSUES_DRAFT_KIND
      else -> AIReviewPromptSupport.DEFAULT_DRAFT_KIND
    }
    return AgentPromptPaletteInitialPrompt(
      kind = kind,
      content = synchronizePrompt(project, AgentPromptPaletteInitialPrompt(kind = kind, content = "")).content,
    )
  }

  override fun classifyPromptDraftKind(project: Project, promptText: String): String {
    val issuesPrompt = AIReviewPromptProvider.getIssuesPrompt(project)
    return when {
      promptText.isBlank() -> getInitialPrompt(project).kind ?: AIReviewPromptSupport.DEFAULT_DRAFT_KIND
      AIReviewPromptSupport.containsIssueBlock(promptText, issuesPrompt) -> AIReviewPromptSupport.ISSUES_DRAFT_KIND
      else -> AIReviewPromptSupport.DEFAULT_DRAFT_KIND
    }
  }

  override fun synchronizePrompt(project: Project, currentPrompt: AgentPromptPaletteInitialPrompt): AgentPromptPaletteInitialPrompt {
    val currentText = currentPrompt.content
    val guidelines = AIReviewPromptSupport.loadProjectReviewGuidelines(project)
    val issuesPrompt = AIReviewPromptProvider.getIssuesPrompt(project)
    if (guidelines.isNotBlank()) {
      val withoutIssues = AIReviewPromptSupport.stripIssueBlocks(currentText, issuesPrompt)
      return currentPrompt.copy(kind = AIReviewPromptSupport.DEFAULT_DRAFT_KIND, content = withoutIssues.ifBlank { guidelines })
    }

    val defaultPrompt = AIReviewPromptProvider.getDefaultPrompt(project)
    val issueUrls = collectIssueUrls(AgentPromptPaletteExtensionContext.getContextItems(project))
    val issuesDraft = AIReviewPromptSupport.renderIssuesPrompt(issuesPrompt, issueUrls)
    val targetKind = if (issueUrls.isNotEmpty()) AIReviewPromptSupport.ISSUES_DRAFT_KIND else AIReviewPromptSupport.DEFAULT_DRAFT_KIND

    if (issueUrls.isNotEmpty()) {
      if (currentText.isBlank()) {
        return currentPrompt.copy(kind = targetKind, content = issuesDraft)
      }

      if (AIReviewPromptSupport.containsIssueBlock(currentText, issuesPrompt)) {
        return currentPrompt.copy(
          kind = targetKind,
          content = AIReviewPromptSupport.refreshIssueBlocks(currentText, issuesPrompt, issueUrls),
        )
      }

      if (currentText.startsWith(defaultPrompt)) {
        return currentPrompt.copy(
          kind = targetKind,
          content = substituteAiReviewBasePrompt(
            currentText = currentText,
            currentBasePrompt = defaultPrompt,
            targetBasePrompt = issuesDraft,
          ),
        )
      }

      return currentPrompt.copy(kind = targetKind)
    }

    if (currentText.isBlank()) {
      return currentPrompt.copy(kind = targetKind, content = defaultPrompt)
    }

    if (AIReviewPromptSupport.containsIssueBlock(currentText, issuesPrompt)) {
      return currentPrompt.copy(
        kind = targetKind,
        content = AIReviewPromptSupport.replaceIssueBlocks(currentText, issuesPrompt, defaultPrompt),
      )
    }

    return currentPrompt.copy(kind = targetKind)
  }

  override fun getSubmitActionId(): String = AIReviewPromptSupport.AI_REVIEW_EXECUTE_ACTION_ID

  override fun getFooterHint(): String = AIReviewBundle.message("popup.palette.footer.hint.ai.review")

  private fun isReviewContext(item: AgentPromptContextItem): Boolean {
    if (item.rendererId == AgentPromptContextRendererIds.VCS_COMMITS) {
      return true
    }
    if (item.itemId != "tree.selection") {
      return false
    }

    val payload = item.payload.objOrNull() ?: return false
    val treeKind = payload.string("treeKind")
    return treeKind == "Changes"
  }

  private fun collectIssueUrls(contextItems: List<AgentPromptContextItem>): List<String> {
    val issueUrls = LinkedHashSet<String>()
    contextItems.asSequence()
      .filter { item -> item.rendererId == AgentPromptContextRendererIds.VCS_COMMITS }
      .mapNotNull(AgentPromptContextItem::payload)
      .mapNotNull { payload -> payload.objOrNull()?.array("entries") }
      .flatten()
      .mapNotNull { value -> value.objOrNull() }
      .mapNotNull { entry -> entry.array("issueUrls") }
      .flatten()
      .mapNotNull { value -> (value as? AgentPromptPayloadValue.Str)?.value?.trim()?.takeIf(String::isNotEmpty) }
      .forEach(issueUrls::add)
    return issueUrls.toList()
  }

  private fun substituteAiReviewBasePrompt(
    currentText: String,
    currentBasePrompt: String,
    targetBasePrompt: String,
  ): String {
    if (currentText.isBlank()) {
      return targetBasePrompt
    }
    if (currentBasePrompt.isBlank()) {
      return combineAiReviewPrompt(targetBasePrompt, currentText)
    }
    if (!currentText.startsWith(currentBasePrompt)) {
      return combineAiReviewPrompt(targetBasePrompt, currentText)
    }

    val suffix = currentText.removePrefix(currentBasePrompt)
    return targetBasePrompt + suffix
  }

  private fun combineAiReviewPrompt(basePrompt: String, suffix: String): String {
    if (basePrompt.isBlank()) {
      return suffix
    }
    if (suffix.isBlank()) {
      return basePrompt
    }
    return basePrompt + "\n\n" + suffix
  }
}
