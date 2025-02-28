package com.intellij.cce.evaluable

import com.intellij.cce.actions.ProjectActionsEnvironment
import com.intellij.cce.core.Session
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.evaluation.EvaluationEnvironment
import com.intellij.cce.evaluation.EvaluationRootInfo
import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.evaluation.StopEvaluationException
import com.intellij.cce.evaluation.data.EvalData
import com.intellij.cce.evaluation.data.EvalMetric
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.cce.interpreter.PresentableEvalData
import com.intellij.cce.interpreter.PresentableFeatureInvoker
import com.intellij.cce.metric.Metric
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.cce.report.CardLayout
import com.intellij.cce.report.CardReportGenerator
import com.intellij.cce.report.FileReportGenerator
import com.intellij.cce.report.GeneratorDirectories
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicReference

/**
 * Represents an evaluation scenario with predefined and easily customizable evaluation report format.
 */
abstract class PresentableEvaluableFeature<T : EvaluationStrategy>(override val name: String) : EvaluableFeature<T> {
  /**
   * how to prepare the context before the feature invocation
   */
  abstract fun getGenerateActionsProcessor(strategy: T, project: Project): GenerateActionsProcessor

  abstract fun getFeatureInvoker(project: Project, strategy: T): PresentableFeatureInvoker

  abstract fun getEvalMetrics(): List<EvalMetric>

  open fun getLayout(): CardLayout? = null

  final override fun getMetrics(): List<Metric> = getEvalMetrics().map { it.buildMetric() }

  override fun getPreliminaryEvaluationSteps(): List<EvaluationStep> = emptyList()

  override fun getEvaluationSteps(config: Config): List<EvaluationStep> = emptyList()

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
        featureInvoker = CustomizableFeatureWrapper(getFeatureInvoker(project, strategy), outputWorkspace, getEvalMetrics())
      )
    }
  }

  override fun getFileReportGenerator(
    filterName: String,
    comparisonFilterName: String,
    inputWorkspaces: List<EvaluationWorkspace>,
    dirs: GeneratorDirectories,
  ): FileReportGenerator {
    val featuresStorages = inputWorkspaces.map { it.featuresStorage }
    val layout = getLayout() ?: inputWorkspaces.first().layout!!
    return CardReportGenerator(featuresStorages, dirs, filterName, comparisonFilterName, layout, getEvalMetrics())
  }

  private fun actions(config: Config) =
    config.actions ?: throw IllegalStateException("Configuration missing project description (actions)")
}

const val LAYOUT_NAME: String = "layout"

private val EvaluationWorkspace.layout: CardLayout? get() =
  readAdditionalStats(LAYOUT_NAME)?.let { CardLayout.gson.fromJson(it.get(LAYOUT_NAME), CardLayout::class.java) }

private class CustomizableFeatureWrapper(
  private val invoker: PresentableFeatureInvoker,
  private val workspace: EvaluationWorkspace,
  private val metrics: List<EvalMetric>
) : FeatureInvoker {
  private val globalLayout = AtomicReference<CardLayout>(null)

  override fun callFeature(expectedText: String, offset: Int, properties: TokenProperties): Session {
    val data = invoker.invoke(properties)
    try {
      processData(data)
      return data.session(expectedText, offset, properties)
    }
    catch (e: Throwable) {
      throw StopEvaluationException("Layout processing problem", e)
    }
  }

  override fun comparator(generated: String, expected: String): Boolean = invoker.comparator(generated, expected)

  private fun processData(data: PresentableEvalData) {
    var existing = globalLayout.get()
    val layout = data.layout
    while (existing == null) {
      if (globalLayout.compareAndSet(null, layout)) {
        workspace.saveAdditionalStats(LAYOUT_NAME, mapOf(LAYOUT_NAME to CardLayout.gson.toJsonTree(layout)))

        val allData = data.allBindings.map { it.bindable.data }
        EvalData.checkUniqueness(allData)
        checkMetrics(allData)
      }
      existing = globalLayout.get()
    }

    check(existing == layout) {
      "Layout should stay the same. \nOld: $existing \nNew: $layout"
    }
  }

  private fun checkMetrics(allData: List<EvalData<*>>) {
    val problemMetrics = metrics
      .map { it to it.dependencies.failedRequirements(allData) }
      .filter { (_, missing) -> missing.isNotEmpty() }

    check(problemMetrics.isEmpty()) {
      problemMetrics.joinToString(separator = "\n") { (metric, missing) ->
        "'${metric.name}' metric requires: ${missing.joinToString(", ")}"
      }
    }
  }
}