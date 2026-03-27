// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.model

import com.intellij.agent.workbench.ai.review.AIReviewBundle
import com.intellij.agent.workbench.ai.review.model.AIReviewResult.Severity
import com.intellij.agent.workbench.ai.review.model.AIReviewResult.Severity.Error
import com.intellij.agent.workbench.ai.review.model.AIReviewResult.Severity.Info
import com.intellij.agent.workbench.ai.review.model.AIReviewResult.Severity.StrongWarning
import com.intellij.agent.workbench.ai.review.model.AIReviewResult.Severity.Typo
import com.intellij.agent.workbench.ai.review.model.AIReviewResult.Severity.Warning
import com.intellij.agent.workbench.ai.review.model.AIReviewResult.Severity.WeakWarning
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest
import com.intellij.openapi.vcs.changes.Change
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.Random
import javax.swing.Icon

/**
 * Provides the context of the request for finding problems within a set of changes.
 *
 * @property selectedAgent selected ACP agent from the review config.
 */
@ApiStatus.Internal
sealed interface AIReviewRequest {
  val requestId: Long
  val selectedAgent: AIReviewAgent?

  /**
   * Review request for local changes.
   *
   * @property initialMessageRequest prompt-popup text and selected context captured on submit.
   * @property selectedAgent selected ACP agent from the review config.
   */
  data class LocalChanges(
    val changes: List<Change>,
    val initialMessageRequest: AgentPromptInitialMessageRequest? = null,
    override val selectedAgent: AIReviewAgent? = null,
    override val requestId: Long = Random().nextLong(),
  ) : AIReviewRequest
}

@ApiStatus.Internal
data class AIReviewAgent(
  val configKey: String,
  val displayName: @Nls String,
  val icon: Icon? = null,
)

/**
 * Represents the result of an AI-powered review for [Change]s.
 *
 * @property request The context of the request for analyzing changes.
 * @property problems The list of problems identified during the review process.
 */
data class AIReviewResult(
  val request: AIReviewRequest,
  val problems: List<Problem> = emptyList(),
) {
  /**
   * Represents a single problem identified by AI.
   *
   * @param id The identifier of the problem should stay the same even
   *           if the problem is moved to a different location.
   */
  data class Problem(
    val id: String,
    val message: String,
    val reasoning: String,
    val path: String,
    val lineStart: Int,
    val lineEnd: Int,
    val severity: Severity,
  )

  enum class Severity {
    //ordinal affects comparator
    Typo,
    Info,
    WeakWarning,
    Warning,
    StrongWarning,
    Error,
  }
}

internal val Severity.displayName
  get(): @Nls String =
    when (this) {
      Typo -> AIReviewBundle.message("aiReview.problems.severity.typo")
      Info -> AIReviewBundle.message("aiReview.problems.severity.info")
      WeakWarning -> AIReviewBundle.message("aiReview.problems.severity.weak.warning")
      Warning -> AIReviewBundle.message("aiReview.problems.severity.warning")
      StrongWarning -> AIReviewBundle.message("aiReview.problems.severity.strong.warning")
      Error -> AIReviewBundle.message("aiReview.problems.severity.strong.error")
    }
