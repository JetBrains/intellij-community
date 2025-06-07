// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.application.ApplicationManager
import kotlin.system.measureTimeMillis

class EvaluationProcess private constructor (
  private val environment: EvaluationEnvironment,
  private val steps: List<EvaluationStep>,
  private val finalStep: FinishEvaluationStep?
) {
  companion object {
    fun build(environment: EvaluationEnvironment, stepFactory: StepFactory, init: Builder.() -> Unit): EvaluationProcess {
      val builder = Builder()
      builder.init()
      return builder.build(environment, stepFactory)
    }
  }

  fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
    val stats = mutableMapOf<String, Long>()
    var currentWorkspace = workspace
    var hasError = false
    for (step in steps) {
      if (hasError && step !is UndoableEvaluationStep.UndoStep) continue
      println("Starting step: ${step.name} (${step.description})")
      val duration = measureTimeMillis {
        val result = environment.execute(step, currentWorkspace)
        if (result == null) {
          hasError = true
        } else {
          currentWorkspace = result
        }
      }
      stats[step.name] = duration
    }
    currentWorkspace.saveAdditionalStats("evaluation_process", stats)
    finalStep?.start(currentWorkspace, hasError)
    return currentWorkspace
  }

  class Builder {
    var shouldGenerateActions: Boolean = false
    var shouldInterpretActions: Boolean = false
    var shouldGenerateReports: Boolean = false
    var shouldReorderElements: Boolean = false

    fun build(environment: EvaluationEnvironment, factory: StepFactory): EvaluationProcess {
      val steps = mutableListOf<EvaluationStep>()
      val isTestingEnvironment = ApplicationManager.getApplication().isUnitTestMode

      if (!isTestingEnvironment && (shouldGenerateActions || shouldInterpretActions)) {
        factory.setupEnvironmentSteps().forEach { steps.add(it) }
      }

      if (shouldInterpretActions) {
        factory.setupStatsCollectorStep()?.let { steps.add(it) }
        steps.add(factory.setupRegistryStep())
        steps.addAll(factory.featureSpecificSteps())
      }

      if (shouldGenerateActions) {
        steps.add(factory.generateActionsStep())
      }

      if (shouldInterpretActions) {
        if (shouldGenerateActions) {
          steps.add(factory.interpretActionsStep())
        }
        else {
          steps.add(factory.interpretActionsOnNewWorkspaceStep())
        }
      }

      if (shouldReorderElements) {
        steps.add(factory.reorderElements())
      }

      if (shouldGenerateReports) {
        steps.add(factory.generateReportStep())
      }

      for (step in steps.reversed()) {
        if (step is UndoableEvaluationStep)
          steps.add(step.undoStep())
      }

      for (step in factory.featureSpecificPreliminarySteps().reversed()) {
        if (step is UndoableEvaluationStep)
          steps.add(step.undoStep())
      }

      return EvaluationProcess(environment, steps, factory.finishEvaluationStep().takeIf { !isTestingEnvironment })
    }
  }
}