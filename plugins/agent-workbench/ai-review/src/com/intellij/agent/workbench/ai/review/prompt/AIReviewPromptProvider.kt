// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.prompt

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AIReviewPromptProvider {
  fun getDefaultPrompt(project: Project): String?

  fun getIssuesPrompt(project: Project): String?

  companion object {
    private val EP_NAME: ExtensionPointName<AIReviewPromptProvider> =
      ExtensionPointName.create("com.intellij.agent.workbench.ai.review.promptProvider")

    fun getDefaultPrompt(project: Project): String {
      return EP_NAME.extensionList
        .firstNotNullOfOrNull { provider -> provider.getDefaultPrompt(project)?.takeIf(String::isNotBlank) }
             ?: AIReviewPromptSupport.DEFAULT_PROMPT
    }

    fun getIssuesPrompt(project: Project): String {
      return EP_NAME.extensionList
        .firstNotNullOfOrNull { provider -> provider.getIssuesPrompt(project)?.takeIf(String::isNotBlank) }
             ?: AIReviewPromptSupport.ISSUES_PROMPT
    }
  }
}
