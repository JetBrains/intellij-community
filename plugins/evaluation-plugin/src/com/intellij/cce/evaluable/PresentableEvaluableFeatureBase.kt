package com.intellij.cce.evaluable

import com.intellij.cce.actions.ProjectActionsEnvironment
import com.intellij.cce.core.Session
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.evaluation.EvaluationEnvironment
import com.intellij.cce.evaluation.EvaluationRootInfo
import com.intellij.cce.evaluation.StopEvaluationException
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.cce.interpreter.PresentableFeatureInvoker
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.project.Project

/**
 * Adds compatibility with [EvaluableFeatureBase] to [PresentableFeature]
 * allowing to use easily customizable evaluation report format for project-based evaluations.
 */
abstract class PresentableEvaluableFeatureBase<T : EvaluationStrategy>(name: String) : PresentableFeature<T>(name) {
  /**
   * how to prepare the context before the feature invocation
   */
  abstract fun getGenerateActionsProcessor(strategy: T, project: Project): GenerateActionsProcessor

  abstract fun getFeatureInvoker(project: Project, strategy: T): PresentableFeatureInvoker

  override fun prepareEnvironment(config: Config, outputWorkspace: EvaluationWorkspace): EvaluationEnvironment {
    val actions = actions(config)
    val strategy = config.strategy<T>()
    return ProjectActionsEnvironment.open(actions.projectPath) { project ->
      ProjectActionsEnvironment(
        strategy,
        actions,
        config.interpret.filesLimit,
        config.interpret.sessionsLimit,
        EvaluationRootInfo(true),
        project,
        getGenerateActionsProcessor(strategy, project),
        name,
        featureInvoker = CustomizableFeatureWrapper(getFeatureInvoker(project, strategy), layoutManager(outputWorkspace))
      )
    }
  }

  private fun actions(config: Config) =
    config.actions ?: throw IllegalStateException("Configuration missing project description (actions)")
}

private class CustomizableFeatureWrapper(
  private val invoker: PresentableFeatureInvoker,
  private val layoutManager: LayoutManager
) : FeatureInvoker {

  override fun callFeature(expectedText: String, offset: Int, properties: TokenProperties): Session {
    val data = invoker.invoke(properties)
    try {
      layoutManager.processData(data)
      return data.session(expectedText, offset, properties)
    }
    catch (e: Throwable) {
      throw StopEvaluationException("Layout processing problem", e)
    }
  }

  override fun comparator(generated: String, expected: String): Boolean = invoker.comparator(generated, expected)
}
