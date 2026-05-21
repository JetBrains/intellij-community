// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.prompt

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptContextRendererIds
import com.intellij.agent.workbench.prompt.core.AgentPromptContextTruncation
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteExtensionContext
import com.intellij.agent.workbench.prompt.core.AgentPromptPaletteInitialPrompt
import com.intellij.agent.workbench.prompt.core.AgentPromptPayloadValue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ThrowableRunnable

class AIReviewPaletteExtensionTest : BasePlatformTestCase() {

  private lateinit var extension: AIReviewPaletteExtension
  private var promptProviderExtensionPointRegistered: Boolean = false

  override fun setUp() {
    super.setUp()
    ensurePromptProviderExtensionPoint()
    extension = AIReviewPaletteExtension()
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable {
        if (promptProviderExtensionPointRegistered) {
          ApplicationManager.getApplication().extensionArea.unregisterExtensionPoint(AI_REVIEW_PROMPT_PROVIDER_EP)
        }
      },
      ThrowableRunnable { super.tearDown() },
    ).run()
  }

  fun `test ai review initial prompt uses issue prompt when commit issues exist`() {
    val prompt = withContextItems(listOf(vcsCommitsContextItem("https://tracker.example.test/issue/CASE-101"))) {
      extension.getInitialPrompt(project).content
    }

    assertEquals(renderedIssueBlock("https://tracker.example.test/issue/CASE-101"), prompt)
  }

  fun `test ai review uses default draft kind when commit issues are absent`() {
    val draftKind = withContextItems(emptyList()) {
      extension.getInitialPrompt(project).kind ?: error("Expected default draft kind")
    }

    assertEquals(AIReviewPromptSupport.DEFAULT_DRAFT_KIND, draftKind)
  }

  fun `test ai review uses issues draft kind when commit issues exist`() {
    val draftKind = withContextItems(listOf(vcsCommitsContextItem("https://tracker.example.test/issue/CASE-202"))) {
      extension.getInitialPrompt(project).kind ?: error("Expected issues draft kind")
    }

    assertEquals(AIReviewPromptSupport.ISSUES_DRAFT_KIND, draftKind)
  }

  fun `test ai review classifies prompt without issue block as default draft`() {
    val currentPrompt = buildString {
      append(defaultAiReviewPrompt())
      append("\n\n")
      append("Also pay attention to migrations.")
    }

    val draftKind = extension.classifyPromptDraftKind(project, currentPrompt)

    assertEquals(AIReviewPromptSupport.DEFAULT_DRAFT_KIND, draftKind)
  }

  fun `test ai review classifies prompt with issue block as issues draft`() {
    val prompt = buildString {
      append(renderedIssueBlock("https://tracker.example.test/issue/CASE-303"))
      append("\n\n")
      append("Also pay attention to migrations.")
    }

    val draftKind = extension.classifyPromptDraftKind(project, prompt)

    assertEquals(AIReviewPromptSupport.ISSUES_DRAFT_KIND, draftKind)
  }

  fun `test ai review prompt synchronization updates issue block within issues draft`() {
    val currentPrompt = buildString {
      append(renderedIssueBlock("https://tracker.example.test/issue/CASE-101"))
      append("\n\n")
      append("Focus on data races.")
    }

    val prompt = synchronizeAiReviewPrompt(
      currentPrompt = currentPrompt,
      contextItems = listOf(vcsCommitsContextItem("https://tracker.example.test/issue/CASE-404")),
    )

    assertEquals(renderedIssueBlock("https://tracker.example.test/issue/CASE-404") + "\n\nFocus on data races.", prompt)
  }

  fun `test ai review prompt synchronization removes issue block when there are no issue urls`() {
    val currentPrompt = buildString {
      append(renderedIssueBlock("https://tracker.example.test/issue/CASE-101"))
      append("\n\n")
      append("Focus on data races.")
    }

    val prompt = synchronizeAiReviewPrompt(currentPrompt = currentPrompt, contextItems = emptyList())

    assertEquals(defaultAiReviewPrompt() + "\n\nFocus on data races.", prompt)
  }

  fun `test ai review prompt synchronization collapses duplicated issue blocks`() {
    val currentPrompt = buildString {
      append(renderedIssueBlock("https://tracker.example.test/issue/CASE-101"))
      append("\n\n")
      append(renderedIssueBlock("https://tracker.example.test/issue/CASE-101"))
      append("\n\n")
      append("Keep this note.")
    }

    val prompt = synchronizeAiReviewPrompt(
      currentPrompt = currentPrompt,
      contextItems = listOf(vcsCommitsContextItem("https://tracker.example.test/issue/CASE-505")),
    )

    assertEquals(
      buildString {
        append(renderedIssueBlock("https://tracker.example.test/issue/CASE-505"))
        append("\n\n")
        append("Keep this note.")
      },
      prompt,
    )
  }

  fun `test ai review prompt synchronization keeps user lines around issue block`() {
    val currentPrompt = buildString {
      append("Look at module boundaries.\n\n")
      append(renderedIssueBlock("https://tracker.example.test/issue/CASE-101"))
      append("\n\nDouble-check generated code.")
    }

    val prompt = synchronizeAiReviewPrompt(
      currentPrompt = currentPrompt,
      contextItems = listOf(vcsCommitsContextItem("https://tracker.example.test/issue/CASE-606")),
    )

    assertEquals(
      buildString {
        append("Look at module boundaries.\n\n")
        append(renderedIssueBlock("https://tracker.example.test/issue/CASE-606"))
        append("\n\nDouble-check generated code.")
      },
      prompt,
    )
  }

  fun `test ai review prompt synchronization preserves user lines around issue list inside issue block`() {
    val currentPrompt = buildString {
      append("Consider the following **bugs** when doing a review:\n\n")
      append("Keep this line before the generated list.\n")
      append("- https://tracker.example.test/issue/CASE-101\n")
      append("Keep this line after the generated list.\n\n")
      append("**IMPORTANT:**\n")
      append("Ensure that the **bugs** do not exist in the given changes.")
    }

    val prompt = synchronizeAiReviewPrompt(
      currentPrompt = currentPrompt,
      contextItems = listOf(vcsCommitsContextItem("https://tracker.example.test/issue/CASE-707")),
    )

    assertEquals(
      buildString {
        append("Consider the following **bugs** when doing a review:\n\n")
        append("Keep this line before the generated list.\n")
        append("- https://tracker.example.test/issue/CASE-707\n")
        append("Keep this line after the generated list.\n\n")
        append("**IMPORTANT:**\n")
        append("Ensure that the **bugs** do not exist in the given changes.")
      },
      prompt,
    )
  }

  private fun synchronizeAiReviewPrompt(currentPrompt: String, contextItems: List<AgentPromptContextItem>): String {
    return withContextItems(contextItems) {
      val initialPrompt = extension.getInitialPrompt(project)
      extension.synchronizePrompt(
        project,
        AgentPromptPaletteInitialPrompt(kind = initialPrompt.kind, content = currentPrompt),
      ).content
    }
  }

  private fun defaultAiReviewPrompt(): String {
    return AIReviewPromptProvider.getDefaultPrompt(project)
  }

  private fun renderedIssueBlock(vararg issueUrls: String): String {
    return AIReviewPromptSupport.renderIssuesPrompt(AIReviewPromptProvider.getIssuesPrompt(project), issueUrls.toList())
  }

  private fun <T> withContextItems(contextItems: List<AgentPromptContextItem>, action: () -> T): T {
    return AgentPromptPaletteExtensionContext.withContextItems(project, contextItems, action)
  }

  private fun vcsCommitsContextItem(vararg issueUrls: String): AgentPromptContextItem {
    return AgentPromptContextItem(
      rendererId = AgentPromptContextRendererIds.VCS_COMMITS,
      title = "Picked Commits",
      body = "abcdef12",
      payload = AgentPromptPayloadValue.Obj(
        mapOf(
          "entries" to AgentPromptPayloadValue.Arr(
            listOf(
              AgentPromptPayloadValue.Obj(
                mapOf(
                  "issueUrls" to AgentPromptPayloadValue.Arr(issueUrls.map(AgentPromptPayloadValue::Str)),
                ),
              ),
            ),
          ),
        ),
      ),
      itemId = "vcsCommits",
      source = "manualVcs",
      truncation = AgentPromptContextTruncation.none(8),
    )
  }

  private fun ensurePromptProviderExtensionPoint() {
    val extensionArea = ApplicationManager.getApplication().extensionArea
    if (extensionArea.hasExtensionPoint(AI_REVIEW_PROMPT_PROVIDER_EP)) {
      return
    }

    extensionArea.registerExtensionPoint(
      AI_REVIEW_PROMPT_PROVIDER_EP,
      AIReviewPromptProvider::class.java.name,
      ExtensionPoint.Kind.INTERFACE,
      true,
    )
    promptProviderExtensionPointRegistered = true
  }

  private companion object {
    const val AI_REVIEW_PROMPT_PROVIDER_EP: String = "com.intellij.agent.workbench.ai.review.promptProvider"
  }
}
