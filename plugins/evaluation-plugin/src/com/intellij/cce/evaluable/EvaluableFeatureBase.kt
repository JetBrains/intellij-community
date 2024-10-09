// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable

import com.intellij.cce.actions.DatasetRef
import com.intellij.cce.actions.ProjectActionsDataset
import com.intellij.cce.core.Language
import com.intellij.cce.evaluation.*
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.cce.report.BasicFileReportGenerator
import com.intellij.cce.report.FileReportGenerator
import com.intellij.cce.report.GeneratorDirectories
import com.intellij.cce.workspace.Config
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

  override fun getFileReportGenerator(filterName: String,
                                      comparisonFilterName: String,
                                      featuresStorages: List<FeaturesStorage>,
                                      fullLineStorages: List<FullLineLogsStorage>,
                                      dirs: GeneratorDirectories): FileReportGenerator =
    BasicFileReportGenerator(filterName, comparisonFilterName, featuresStorages, dirs)

  override fun getPreliminaryEvaluationSteps(): List<EvaluationStep> = emptyList()

  override fun prepareEnvironment(config: Config): EvaluationEnvironment {
    val actions = actions(config)
    val strategy = config.strategy<T>()
    return ProjectEnvironment.open(actions.projectPath) { project ->
      StandaloneEnvironment(
        dataset = ProjectActionsDataset(
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
      )
    }
  }

  private fun actions(config: Config) =
    config.actions ?: throw IllegalStateException("Configuration missing project description (actions)")
}
