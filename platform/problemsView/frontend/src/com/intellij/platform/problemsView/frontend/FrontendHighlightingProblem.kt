package com.intellij.platform.problemsView.frontend

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.analysis.problemsView.toolWindow.splitApi.HighlightingProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.HighlightingBaseProblem
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.actions.QuickFixDto
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

private val LOG = logger<FrontendHighlightingProblem>()

@ApiStatus.Internal
class FrontendHighlightingProblem : FileProblem, HighlightingBaseProblem {
  constructor(myProject: Project, file: VirtualFile, dto: HighlightingProblemDto) {
    this.file = file
    this.id = dto.id
    this.text = dto.text
    this.line = dto.line
    this.column = dto.column
    this.group = dto.group
    this.description = dto.description
    this.severity = dto.severity.value
    this.icon = dto.iconId?.icon() ?: HighlightDisplayLevel.ERROR.icon
    this.quickFixes = dto.quickFixes
    this.quickFixOffset = dto.quickFixOffset
    this.contextGroup = dto.contextGroup?.let { FrontendCodeInsightContext(it) }
    this.provider = object : ProblemsProvider {
      override val project = myProject
    }
  }

  val id: String
  override val file: VirtualFile
  override val text: String
  override val line: Int
  override val column: Int
  override val group: String?
  override val description: String?
  override val severity: Int
  override val icon: Icon
  override val provider: ProblemsProvider
  override val contextGroup: CodeInsightContext?

  private val quickFixes: List<QuickFixDto>
  private val quickFixOffset: Int

  fun hasQuickFixes(): Boolean = quickFixes.isNotEmpty()

  fun getQuickFixes(): List<QuickFixDto> = quickFixes

  override fun getQuickFixOffset(): Int = quickFixOffset
}

internal fun convertDtoToHighlightingProblem(dto: ProblemDto, file: VirtualFile, project: Project): FrontendHighlightingProblem? {
  return when (dto) {
    is HighlightingProblemDto -> FrontendHighlightingProblem(project, file, dto)
    else -> {
      LOG.warn("ignoring problem dto")
      null
    }
  }
}

private data class FrontendCodeInsightContext(val name: String) : CodeInsightContext {
  override fun toString(): String = name
}
