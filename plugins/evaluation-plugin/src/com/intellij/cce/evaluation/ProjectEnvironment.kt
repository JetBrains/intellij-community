package com.intellij.cce.evaluation

import com.intellij.cce.actions.EvaluationDataset
import com.intellij.cce.actions.OpenProjectArgsData
import com.intellij.cce.actions.ProjectOpeningUtils
import com.intellij.cce.evaluation.step.runInIntellij
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.project.Project
import com.intellij.warmup.util.importOrOpenProjectAsync
import java.nio.file.FileSystems

class ProjectEnvironment(
  val project: Project,
  override val dataset: EvaluationDataset,
) : EvaluationEnvironment {

  override fun execute(step: EvaluationStep, workspace: EvaluationWorkspace): EvaluationWorkspace? =
    step.runInIntellij(project, workspace)

  override fun close() {
    ProjectOpeningUtils.closeProject(project)
  }

  companion object {
    fun open(projectPath: String, init: (Project) -> StandaloneEnvironment): ProjectEnvironment {
      println("Open and load project $projectPath. Operation may take a few minutes.")
      @Suppress("DEPRECATION")
      val project = runUnderModalProgressIfIsEdt {
        importOrOpenProjectAsync(OpenProjectArgsData(FileSystems.getDefault().getPath(projectPath)))
      }
      println("Project loaded!")

      val environment = try {
        init(project)
      }
      catch (exception: Exception) {
        ProjectOpeningUtils.closeProject(project)
        throw RuntimeException("Failed to initialize project environment: $exception", exception)
      }

      return ProjectEnvironment(
        project,
        environment.dataset
      )
    }
  }
}