// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.codegen.writer

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils.selectProjectErrorsTab
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.devkit.workspaceModel.metaModel.ObjMetaElementWithSource
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.refactoring.suggested.startOffset
import com.intellij.workspaceModel.codegen.engine.GenerationProblem
import com.intellij.workspaceModel.codegen.engine.ProblemLocation
import org.jetbrains.kotlin.resolve.source.getPsi
import javax.swing.Icon

@Service
class WorkspaceCodegenProblemsProvider(override val project: Project): ProblemsProvider {
  private var currentProblems = emptyList<WorkspaceModelCodeGenerationProblem>()
  
  companion object {
    fun getInstance(project: Project): WorkspaceCodegenProblemsProvider = project.service()
  }

  fun reportProblems(generationProblems: List<GenerationProblem>) {
    if (generationProblems.isNotEmpty() && ApplicationManager.getApplication().isUnitTestMode) {
      val problem = generationProblems.first()
      error("Failed to generate code for ${problem.location}: ${problem.message}")
    }
    
    val problems = generationProblems.map { it.toProblem() }
    for (currentProblem in currentProblems) {
      ProblemsCollector.getInstance(project).problemDisappeared(currentProblem)
    }
    currentProblems = problems
    for (currentProblem in problems) {
      ProblemsCollector.getInstance(project).problemAppeared(currentProblem)
    }
    if (problems.isNotEmpty()) {
      selectProjectErrorsTab(project)
    }
  }

  private fun GenerationProblem.toProblem(): WorkspaceModelCodeGenerationProblem {
    val sourceElement = when (val problemLocation = location) {
      is ProblemLocation.Class -> (problemLocation.objClass as ObjMetaElementWithSource).sourceElement
      is ProblemLocation.Property -> (problemLocation.property as ObjMetaElementWithSource).sourceElement
    }
    val psiElement = sourceElement.getPsi() ?: return WorkspaceModelCodeGenerationProblem(this) 
    val psiToHighlight = (psiElement as? PsiNameIdentifierOwner)?.nameIdentifier ?: psiElement
    val file = psiToHighlight.containingFile.virtualFile
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return WorkspaceModelCodeGenerationProblem(this)
    val offset = psiToHighlight.startOffset
    val line = document.getLineNumber(offset)
    val column = offset - document.getLineStartOffset(line)
    return WorkspaceModelCodeGenerationProblemInFile(this, file, line, column)
  }

  private open inner class WorkspaceModelCodeGenerationProblem(private val originalProblem: GenerationProblem) : Problem {
    override val provider: ProblemsProvider
      get() = this@WorkspaceCodegenProblemsProvider
    
    override val text: String
      get() = originalProblem.message

    override val icon: Icon
      get() = when (originalProblem.level) {
        GenerationProblem.Level.ERROR -> HighlightDisplayLevel.ERROR.icon
        GenerationProblem.Level.WARNING -> HighlightDisplayLevel.WARNING.icon
      }
  }
  
  private open inner class WorkspaceModelCodeGenerationProblemInFile(
    originalProblem: GenerationProblem,
    override val file: VirtualFile,
    override val line: Int,
    override val column: Int
  ) : WorkspaceModelCodeGenerationProblem(originalProblem), FileProblem
}

