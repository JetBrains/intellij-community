// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.ui

import com.intellij.agent.workbench.ai.review.model.AIReviewResult
import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.util.Objects
import javax.swing.Icon
import kotlin.math.max

@ApiStatus.Internal
class AIReviewFileProblem(
  project: Project,
  override val file: VirtualFile,
  val reviewProblem: AIReviewResult.Problem,
) : FileProblem {
  override val provider: ProblemsProvider = AIReviewFileProblemsProvider(project)

  val severity: AIReviewResult.Severity
    get() = reviewProblem.severity

  override val description: String
    get() = reviewProblem.reasoning

  override val text: String
    get() = reviewProblem.message

  override val line: Int
    get() = max(0, reviewProblem.lineStart - 1)

  val lineEnd: Int
    get() = max(0, reviewProblem.lineEnd - 1)

  override val icon: Icon
    get() = when (reviewProblem.severity) {
      AIReviewResult.Severity.Error -> HighlightDisplayLevel.ERROR.icon
      AIReviewResult.Severity.StrongWarning -> HighlightDisplayLevel.WARNING.icon
      AIReviewResult.Severity.Warning -> HighlightDisplayLevel.WARNING.icon
      AIReviewResult.Severity.WeakWarning -> HighlightDisplayLevel.WEAK_WARNING.icon
      AIReviewResult.Severity.Info -> HighlightDisplayLevel.INFO.icon
      AIReviewResult.Severity.Typo -> HighlightDisplayLevel.WEAK_WARNING.icon
    }

  override fun hashCode(): Int = Objects.hash(file, reviewProblem)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (this.javaClass != other?.javaClass) return false
    val that = other as? AIReviewFileProblem ?: return false
    return that.file == file && that.reviewProblem == reviewProblem
  }

  override fun toString(): String {
    return "Problem(file=${file.path}, severity=$severity, text='$text', lines=$line-$lineEnd)"
  }
}

private class AIReviewFileProblemsProvider(override val project: Project) : ProblemsProvider
