// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable

import com.intellij.cce.actions.ProjectActionsEnvironment
import com.intellij.cce.core.Language
import com.intellij.cce.evaluation.EvaluationEnvironment
import com.intellij.cce.evaluation.EvaluationRootInfo
import com.intellij.cce.evaluation.EvaluationStep
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

abstract class EvaluableFeatureBase<T : EvaluationStrategy>(override val name: String) : EvaluableFeature<T> {

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
    getEvaluationSteps(Language.resolve(actions(config).language), config.strategy())

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
        featureInvoker = getFeatureInvoker(project, Language.resolve(actions.language), strategy)
      )
    }
  }

  private fun actions(config: Config) =
    config.actions ?: throw IllegalStateException("Configuration missing project description (actions)")
}
