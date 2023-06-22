package com.intellij.cce.evaluation

import com.intellij.cce.evaluation.step.FinishEvaluationStep
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import kotlin.system.measureTimeMillis

class EvaluationProcess private constructor(private val steps: List<EvaluationStep>) {
  companion object {
    fun build(init: Builder.() -> Unit, stepFactory: StepFactory): EvaluationProcess {
      val builder = Builder()
      builder.init()
      return builder.build(stepFactory)
    }
  }

  fun startAsync(workspace: EvaluationWorkspace) = ApplicationManager.getApplication().executeOnPooledThread {
    start(workspace)
  }

  fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
    val stats = mutableMapOf<String, Long>()
    var currentWorkspace = workspace
    var hasError = false
    for (step in steps) {
      if (hasError && step !is UndoableEvaluationStep.UndoStep
          && step !is FinishEvaluationStep) continue
      println("Starting step: ${step.name} (${step.description})")
      val duration = measureTimeMillis {
        val result = step.start(currentWorkspace)
        if (result == null) hasError = true
        else currentWorkspace = result
      }
      stats[step.name] = duration
    }
    currentWorkspace.saveAdditionalStats("evaluation_process", stats)
    return currentWorkspace
  }

  class Builder {
    var shouldGenerateActions: Boolean = false
    var shouldInterpretActions: Boolean = false
    var shouldGenerateReports: Boolean = false
    var shouldReorderElements: Boolean = false
    var shouldHighlightInIde: Boolean = false

    fun build(factory: StepFactory): EvaluationProcess {
      val steps = mutableListOf<EvaluationStep>()
      val isTestingEnvironment = ApplicationManager.getApplication().isUnitTestMode

      if (!isTestingEnvironment && (shouldGenerateActions || shouldInterpretActions)) {
        factory.setupSdkStep()?.let { steps.add(it) }

        if (!Registry.`is`("evaluation.plugin.disable.sdk.check")) {
          steps.add(factory.checkSdkConfiguredStep())
        }
      }

      if (shouldInterpretActions) {
        factory.setupStatsCollectorStep()?.let { steps.add(it) }
        steps.add(factory.setupCompletionStep())
        steps.add(factory.setupFullLineStep())
      }

      if (shouldGenerateActions) {
        steps.add(factory.generateActionsStep())
      }

      if (shouldInterpretActions) {
        if (shouldGenerateActions) {
          steps.add(factory.interpretActionsStep())
        } else {
          steps.add(factory.interpretActionsOnNewWorkspaceStep())
        }
      }

      if (shouldReorderElements) {
        steps.add(factory.reorderElements())
      }

      if (shouldHighlightInIde) {
        steps.add(factory.highlightTokensInIdeStep())
      }

      if (shouldGenerateReports) {
        steps.add(factory.generateReportStep())
      }

      for (step in steps.reversed()) {
        if (step is UndoableEvaluationStep)
          steps.add(step.undoStep())
      }

      if (!isTestingEnvironment) {
        steps.add(factory.finishEvaluationStep())
      }

      return EvaluationProcess(steps)
    }
  }
}