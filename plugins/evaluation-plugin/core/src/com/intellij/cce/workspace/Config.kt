// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace

import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.interpreter.InterpretationOrder
import com.intellij.cce.workspace.filter.CompareSessionsFilter
import com.intellij.cce.workspace.filter.NamedFilter
import com.intellij.cce.workspace.filter.SessionsFilter
import java.nio.file.Paths
import kotlin.io.path.absolute


/**
 * Represents a configuration of the evaluation process.
 *
 * @property projectPath The path to the project that will be used for the evaluation.
 * @property projectName The name of the project. It may differ from the root directory name.
 * @property language The programming language whose files are used in the evaluation.
 * @property outputDir The output directory for the evaluation results.
 * @property strategy The evaluation strategy used.
 * @property actions The configuration for actions generation step.
 * @property interpret The configuration for actions interpretation step.
 * @property reorder The configuration for element reordering step.
 * @property reports The configuration for report generation step.
 */
data class Config private constructor(
  val projectPath: String,
  val projectName: String,
  val language: String,
  val outputDir: String,
  val strategy: EvaluationStrategy,
  val actions: ActionsGeneration,
  val interpret: ActionsInterpretation,
  val reorder: ReorderElements,
  val reports: ReportGeneration
) {
  companion object {
    fun build(projectPath: String, language: String, init: Builder.() -> Unit): Config {
      val builder = Builder(projectPath, language)
      builder.init()
      return builder.build()
    }

    fun buildFromConfig(config: Config, update: Builder.() -> Unit): Config {
      val builder = Builder(config)
      builder.update()
      return builder.build()
    }
  }

  /**
   * Represents the configuration for generating actions.
   *
   * @property evaluationRoots The list of evaluation roots. Directories and files with relative and absolute paths are allowed.
   * @property ignoreFileNames The set of file names to ignore. Files and directories with these names inside [evaluationRoots] will be skipped.
   */
  data class ActionsGeneration internal constructor(
    val evaluationRoots: List<String>,
    val ignoreFileNames: Set<String>,
  )

  /**
   * Represents the configuration for the interpretation of actions.
   *
   * @property experimentGroup The ID of A/B experiment group.
   * @property sessionsLimit The limit of sessions in the evaluation.
   * @property filesLimit The limit of files in the evaluation.
   * @property sessionProbability The probability of a session being evaluated.
   * @property sessionSeed The seed for the random session sampling.
   * @property order The order of session interpretation.
   * @property saveLogs Whether to save logs.
   * @property saveFeatures Whether to save ML features for rendering them in reports.
   * @property saveContent Whether to save the content of files.
   * @property logLocationAndItemText Whether to log location and item text in detailed ranking logs.
   * @property trainTestSplit The train test split for detailed ranking logs.
   */
  data class ActionsInterpretation internal constructor(
    val experimentGroup: Int?,
    val sessionsLimit: Int?,
    val filesLimit: Int?,
    val sessionProbability: Double,
    val sessionSeed: Long?,
    val order: InterpretationOrder,
    val saveLogs: Boolean,
    val saveFeatures: Boolean,
    val saveContent: Boolean,
    val logLocationAndItemText: Boolean,
    val trainTestSplit: Int)

  /**
   * Represents the configuration for reordering elements step.
   *
   * @property useReordering Whether to use element reordering.
   * @property title The title of the reordering in reports.
   * @property features The list of ML features to be used for reordering.
   */
  data class ReorderElements internal constructor(
    val useReordering: Boolean,
    val title: String,
    val features: List<String>
  )

  /**
   * Represents the configuration for generating reports step.
   *
   * @property evaluationTitle The title of the evaluation.
   * @property defaultMetrics The list of default metrics rendered in the report.
   * @property sessionsFilters The list of session filters. These filters allow computing metrics and render reports on a subset of sessions.
   * @property comparisonFilters The list of comparison filters. These filters allow subsetting sessions based on multiple evaluations.
   */
  data class ReportGeneration internal constructor(
    val evaluationTitle: String,
    val defaultMetrics: List<String>?,
    val sessionsFilters: List<SessionsFilter>,
    val comparisonFilters: List<CompareSessionsFilter>)

  class Builder internal constructor(private val projectPath: String, private val language: String) {
    var evaluationRoots = mutableListOf<String>()
    var ignoreFileNames = mutableSetOf<String>()
    var projectName = projectPath.split('/').last()
    var outputDir: String = Paths.get(projectPath, "completion-evaluation").toAbsolutePath().toString()
    var strategy: EvaluationStrategy = EvaluationStrategy.defaultStrategy
    var saveLogs = false
    var saveFeatures = true
    var saveContent = false
    var logLocationAndItemText = false
    var trainTestSplit: Int = 70
    var evaluationTitle: String = "BASIC"
    var experimentGroup: Int? = null
    var sessionsLimit: Int? = null
    var filesLimit: Int? = null
    var sessionProbability: Double = 1.0
    var sessionSeed: Long? = null
    var order: InterpretationOrder = InterpretationOrder.LINEAR
    var useReordering: Boolean = false
    var reorderingTitle: String = evaluationTitle
    var featuresForReordering = mutableListOf<String>()
    val filters: MutableMap<String, EvaluationFilter> = mutableMapOf()
    var defaultMetrics: List<String>? = null
    private val sessionsFilters: MutableList<SessionsFilter> = mutableListOf()
    private val comparisonFilters: MutableList<CompareSessionsFilter> = mutableListOf()

    constructor(config: Config) : this(config.projectPath, config.language) {
      outputDir = config.outputDir
      strategy = config.strategy
      evaluationRoots.addAll(config.actions.evaluationRoots)
      ignoreFileNames.addAll(config.actions.ignoreFileNames)
      saveLogs = config.interpret.saveLogs
      saveFeatures = config.interpret.saveFeatures
      saveContent = config.interpret.saveContent
      logLocationAndItemText = config.interpret.logLocationAndItemText
      trainTestSplit = config.interpret.trainTestSplit
      experimentGroup = config.interpret.experimentGroup
      sessionsLimit = config.interpret.sessionsLimit
      filesLimit = config.interpret.filesLimit
      sessionProbability = config.interpret.sessionProbability
      sessionSeed = config.interpret.sessionSeed
      useReordering = config.reorder.useReordering
      reorderingTitle = config.reorder.title
      featuresForReordering.addAll(config.reorder.features)
      evaluationTitle = config.reports.evaluationTitle
      defaultMetrics = config.reports.defaultMetrics
      mergeFilters(config.reports.sessionsFilters)
      mergeComparisonFilters(config.reports.comparisonFilters)
    }

    fun mergeFilters(filters: List<SessionsFilter>) = merge(filters, sessionsFilters as MutableList<NamedFilter>)
    fun mergeComparisonFilters(filters: List<CompareSessionsFilter>) = merge(filters, comparisonFilters as MutableList<NamedFilter>)

    private fun merge(filters: List<NamedFilter>, existedFilters: MutableList<NamedFilter>) {
      for (filter in filters) {
        if (existedFilters.all { it.name != filter.name })
          existedFilters.add(filter)
        else
          println("More than one filter has name ${filter.name}")
      }
    }

    fun build(): Config = Config(
      Paths.get(projectPath).absolute().toString(),
      projectName,
      language,
      outputDir,
      strategy,
      ActionsGeneration(
        evaluationRoots,
        ignoreFileNames,
      ),
      ActionsInterpretation(
        experimentGroup,
        sessionsLimit,
        filesLimit,
        sessionProbability,
        sessionSeed,
        order,
        saveLogs,
        saveFeatures,
        saveContent,
        logLocationAndItemText,
        trainTestSplit
      ),
      ReorderElements(
        useReordering,
        reorderingTitle,
        featuresForReordering
      ),
      ReportGeneration(
        evaluationTitle,
        defaultMetrics,
        sessionsFilters,
        comparisonFilters
      )
    )
  }
}
