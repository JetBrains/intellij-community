// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace

import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.interpreter.InterpretationOrder
import com.intellij.cce.workspace.filter.CompareSessionsFilter
import com.intellij.cce.workspace.filter.NamedFilter
import com.intellij.cce.workspace.filter.SessionsFilter
import java.nio.file.Paths
import kotlin.io.path.absolute

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

  data class ActionsGeneration internal constructor(
    val evaluationRoots: List<String>
  )

  data class ActionsInterpretation internal constructor(
    val experimentGroup: Int?,
    val sessionsLimit: Int?,
    val sessionProbability: Double,
    val sessionSeed: Long?,
    val order: InterpretationOrder,
    val saveLogs: Boolean,
    val saveFeatures: Boolean,
    val saveContent: Boolean,
    val logLocationAndItemText: Boolean,
    val trainTestSplit: Int)

  data class ReorderElements internal constructor(
    val useReordering: Boolean,
    val title: String,
    val features: List<String>
  )

  data class ReportGeneration internal constructor(
    val evaluationTitle: String,
    val defaultMetrics: List<String>?,
    val sessionsFilters: List<SessionsFilter>,
    val comparisonFilters: List<CompareSessionsFilter>)

  class Builder internal constructor(private val projectPath: String, private val language: String) {
    var evaluationRoots = mutableListOf<String>()
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
      saveLogs = config.interpret.saveLogs
      saveFeatures = config.interpret.saveFeatures
      saveContent = config.interpret.saveContent
      logLocationAndItemText = config.interpret.logLocationAndItemText
      trainTestSplit = config.interpret.trainTestSplit
      experimentGroup = config.interpret.experimentGroup
      sessionsLimit = config.interpret.sessionsLimit
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
        evaluationRoots
      ),
      ActionsInterpretation(
        experimentGroup,
        sessionsLimit,
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
