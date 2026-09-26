// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.frontend

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.analysis.problemsView.toolWindow.splitApi.FileProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.GenericProblemDto
import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemDto
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

private val LOG = logger<FrontendGenericProblem>()

@ApiStatus.Internal
interface FrontendProblemWithId : Problem {
  val id: String
}

@ApiStatus.Internal
class FrontendFileProblem : FileProblem, FrontendProblemWithId {
  constructor(myProject: Project, dto: FileProblemDto, file: VirtualFile) {
    this.id = dto.id
    this.text = dto.text
    this.description = dto.description.takeIf { it.isNotEmpty() }
    this.icon = dto.icon?.icon() ?: HighlightDisplayLevel.ERROR.icon
    this.file = file
    this.line = dto.line
    this.column = dto.column
    this.provider = object : ProblemsProvider {
      override val project = myProject
    }
  }

  override val id: String
  override val text: String
  override val description: String?
  override val icon: Icon
  override val file: VirtualFile
  override val line: Int
  override val column: Int
  override val provider: ProblemsProvider
}

@ApiStatus.Internal
class FrontendGenericProblem : Problem, FrontendProblemWithId {
  constructor(myProject: Project, dto: GenericProblemDto) {
    this.id = dto.id
    this.text = dto.text
    this.description = dto.description.takeIf { it.isNotEmpty() }
    this.icon = dto.icon?.icon() ?: HighlightDisplayLevel.ERROR.icon
    this.provider = object : ProblemsProvider {
      override val project = myProject
    }
  }

  override val id: String
  override val text: String
  override val description: String?
  override val icon: Icon
  override val provider: ProblemsProvider
}

internal fun convertDtoToProjectProblem(dto: ProblemDto, project: Project): FrontendProblemWithId? {
  return when (dto) {
    is FileProblemDto -> {
      val file = dto.fileId.virtualFile() ?: return null
      FrontendFileProblem(project, dto, file)
    }
    is GenericProblemDto -> { FrontendGenericProblem(project, dto) }
    else -> {
      LOG.warn("ignoring problem dto")
      null
    }
  }
}
