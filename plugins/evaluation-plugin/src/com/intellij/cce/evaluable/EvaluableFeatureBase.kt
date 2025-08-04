// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable

import com.intellij.cce.actions.ProjectActionsEnvironment
import com.intellij.cce.core.Language
import com.intellij.cce.evaluation.EvaluationEnvironment
import com.intellij.cce.evaluation.EvaluationRootInfo
import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.evaluation.SetupSdkPreferences
import com.intellij.cce.evaluation.SetupSdkStepFactory
import com.intellij.cce.evaluation.step.CheckProjectSdkStep
import com.intellij.cce.evaluation.step.DropProjectSdkStep
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.cce.report.BasicFileReportGenerator
import com.intellij.cce.report.FileReportGenerator
import com.intellij.cce.report.GeneratorDirectories
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.cce.workspace.storages.FeaturesStorage
import com.intellij.cce.workspace.storages.FullLineLogsStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry

abstract class EvaluableFeatureBase<T : EvaluationStrategy>(override val name: String) : EvaluableFeature<T> {
  open val setupSdkPreferences: SetupSdkPreferences = SetupSdkPreferences(
    resolveDeps = false
  )

  /**
   * how to prepare the context before the feature invocation
   */
  abstract fun getGenerateActionsProcessor(strategy: T, project: Project): GenerateActionsProcessor

  /**
   * how to call the feature
   */
  abstract fun getFeatureInvoker(project: Project, language: Language, strategy: T): FeatureInvoker

  abstract fun getEvaluationSteps(language: Language, strategy: T): List<EvaluationStep>

  override fun getEvaluationSteps(config: Config): List<EvaluationStep> =
    getEvaluationSteps(actions(config).language?.let { Language.resolve(it) } ?: Language.ANOTHER, config.strategy())

  open fun getSetupSteps(project: Project, language: Language, strategy: T): List<EvaluationStep> =
    defaultSetupSteps(project, language, setupSdkPreferences)

  open fun getFileReportGenerator(
    filterName: String,
    comparisonFilterName: String,
    featuresStorages: List<FeaturesStorage>,
    fullLineStorages: List<FullLineLogsStorage>,
    dirs: GeneratorDirectories,
  ): FileReportGenerator =
    BasicFileReportGenerator(filterName, comparisonFilterName, featuresStorages, dirs)

  override fun getFileReportGenerator(
    filterName: String,
    comparisonFilterName: String,
    inputWorkpaces: List<EvaluationWorkspace>,
    dirs: GeneratorDirectories,
  ): FileReportGenerator {
    val featuresStorages = inputWorkpaces.map { it.featuresStorage }
    val fullLineStorages = inputWorkpaces.map { it.fullLineLogsStorage }
    return getFileReportGenerator(filterName, comparisonFilterName, featuresStorages, fullLineStorages, dirs)
  }

  override fun getPreliminaryEvaluationSteps(): List<EvaluationStep> = emptyList()

  override fun prepareEnvironment(config: Config, outputWorkspace: EvaluationWorkspace): EvaluationEnvironment {
    val actions = actions(config)
    val strategy = config.strategy<T>()
    val language = actions.language?.let { Language.resolve(it) } ?: Language.ANOTHER

    return ProjectActionsEnvironment.open(actions.projectPath) { project ->
      ProjectActionsEnvironment(
        strategy,
        actions,
        config.interpret.filesLimit,
        config.interpret.sessionsLimit,
        EvaluationRootInfo(true),
        project,
        getGenerateActionsProcessor(strategy, project),
        getSetupSteps(project, language, strategy),
        name,
        featureInvoker = getFeatureInvoker(project, language, strategy)
      )
    }
  }

  private fun actions(config: Config) =
    config.actions ?: throw IllegalStateException("Configuration missing project description (actions)")
}

fun defaultSetupSteps(project: Project, language: Language, preferences: SetupSdkPreferences): List<EvaluationStep> {
  val dropStep = listOf(DropProjectSdkStep(project)).filter { System.getenv("EVALUATION_PROJECT_SDK_DROP") == "true" }
  val setupSteps = SetupSdkStepFactory.forLanguage(project, language)?.steps(preferences) ?: emptyList()
  val checkStep = CheckProjectSdkStep(project, language.displayName).takeUnless { Registry.`is`("evaluation.plugin.disable.sdk.check") }
  return dropStep + setupSteps + listOfNotNull(checkStep)
}
