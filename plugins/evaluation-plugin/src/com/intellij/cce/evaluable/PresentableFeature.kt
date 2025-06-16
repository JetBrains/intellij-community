package com.intellij.cce.evaluable

import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.evaluation.StopEvaluationException
import com.intellij.cce.evaluation.data.EvalData
import com.intellij.cce.evaluation.data.EvalMetric
import com.intellij.cce.interpreter.PresentableEvalData
import com.intellij.cce.metric.Metric
import com.intellij.cce.report.CardLayout
import com.intellij.cce.report.CardReportGenerator
import com.intellij.cce.report.FileReportGenerator
import com.intellij.cce.report.GeneratorDirectories
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.progress.runBlockingCancellable
import java.util.concurrent.atomic.AtomicReference

/**
 * Represents an evaluation scenario with predefined and easily customizable evaluation report format.
 */
abstract class PresentableFeature<T : EvaluationStrategy>(override val name: String) : EvaluableFeature<T> {
  abstract fun getEvalMetrics(): List<EvalMetric>
  override fun getMetrics(): List<Metric> = getEvalMetrics().map { it.metricBuilder() }

  override fun getPreliminaryEvaluationSteps(): List<EvaluationStep> = emptyList()
  override fun getEvaluationSteps(config: Config): List<EvaluationStep> = emptyList()

  protected open fun getLayout(): CardLayout? = null
  protected open fun getAugmenters(): List<PresentableEvalData.Augmenter> = emptyList()

  override fun getFileReportGenerator(
    filterName: String,
    comparisonFilterName: String,
    inputWorkspaces: List<EvaluationWorkspace>,
    dirs: GeneratorDirectories,
  ): FileReportGenerator {
    val featuresStorages = inputWorkspaces.map { it.featuresStorage }
    val layout = getLayout() ?: inputWorkspaces.first().layout
                 ?: throw IllegalStateException("Layout not found. It could be not generated during evaluation if there were no sessions.")
    return CardReportGenerator(featuresStorages, dirs, filterName, comparisonFilterName, layout, getEvalMetrics())
  }

  protected fun layoutManager(outputWorkspace: EvaluationWorkspace): LayoutManager =
    LayoutManager(outputWorkspace, getEvalMetrics(), getAugmenters())
}

const val LAYOUT_NAME: String = "layout"

private val EvaluationWorkspace.layout: CardLayout? get() =
  readAdditionalStats(LAYOUT_NAME)?.let { CardLayout.gson.fromJson(it.get(LAYOUT_NAME), CardLayout::class.java) }

class LayoutManager(
  private val workspace: EvaluationWorkspace,
  private val metrics: List<EvalMetric>,
  private val augmenters: List<PresentableEvalData.Augmenter>
) {
  private val globalLayout = AtomicReference(workspace.layout)

  fun processData(f: suspend () -> PresentableEvalData): PresentableEvalData {
    val data = runBlockingCancellable {
      PresentableEvalData.augment(augmenters) {
        f()
      }
    }
    try {
      processData(data)
      return data
    } catch (e: Throwable) {
      throw StopEvaluationException("Layout processing failed", e)
    }
  }

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