package com.intellij.cce.workspace

import com.intellij.cce.actions.*
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.workspace.filter.CompareSessionsFilter
import com.intellij.cce.workspace.filter.NamedFilter
import com.intellij.cce.workspace.filter.SessionsFilter
import java.nio.file.Paths
import kotlin.io.path.absolute

data class Config private constructor(
  val projectPath: String,
  val language: String,
  val outputDir: String,
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
    val evaluationRoots: List<String>,
    val strategy: CompletionStrategy)

  data class ActionsInterpretation internal constructor(
    val completionType: CompletionType,
    val experimentGroup: Int?,
    val sessionsLimit: Int?,
    val completeTokenProbability: Double,
    val completeTokenSeed: Long?,
    val emulationSettings: UserEmulator.Settings?,
    val completionGolfSettings: CompletionGolfEmulation.Settings?,
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
    val sessionsFilters: List<SessionsFilter>,
    val comparisonFilters: List<CompareSessionsFilter>)

  class Builder internal constructor(private val projectPath: String, private val language: String) {
    var evaluationRoots = mutableListOf<String>()
    var outputDir: String = Paths.get(projectPath, "completion-evaluation").toAbsolutePath().toString()
    var saveLogs = false
    var saveFeatures = true
    var saveContent = false
    var logLocationAndItemText = false
    var trainTestSplit: Int = 70
    var completionType: CompletionType = CompletionType.BASIC
    var evaluationTitle: String = completionType.name
    var prefixStrategy: CompletionPrefix = CompletionPrefix.NoPrefix
    var contextStrategy: CompletionContext = CompletionContext.ALL
    var experimentGroup: Int? = null
    var sessionsLimit: Int? = null
    var emulateUser: Boolean = false
    var completionGolf: Boolean = false
    var emulationSettings: UserEmulator.Settings? = null
    var completionGolfSettings: CompletionGolfEmulation.Settings? = null
    var completeTokenProbability: Double = 1.0
    var completeTokenSeed: Long? = null
    var useReordering: Boolean = false
    var reorderingTitle: String = evaluationTitle
    var featuresForReordering = mutableListOf<String>()
    val filters: MutableMap<String, EvaluationFilter> = mutableMapOf()
    private val sessionsFilters: MutableList<SessionsFilter> = mutableListOf()
    private val comparisonFilters: MutableList<CompareSessionsFilter> = mutableListOf()

    constructor(config: Config) : this(config.projectPath, config.language) {
      outputDir = config.outputDir
      evaluationRoots.addAll(config.actions.evaluationRoots)
      prefixStrategy = config.actions.strategy.prefix
      contextStrategy = config.actions.strategy.context
      emulateUser = config.actions.strategy.emulateUser
      filters.putAll(config.actions.strategy.filters)
      saveLogs = config.interpret.saveLogs
      saveFeatures = config.interpret.saveFeatures
      saveContent = config.interpret.saveContent
      logLocationAndItemText = config.interpret.logLocationAndItemText
      trainTestSplit = config.interpret.trainTestSplit
      completionType = config.interpret.completionType
      experimentGroup = config.interpret.experimentGroup
      sessionsLimit = config.interpret.sessionsLimit
      emulationSettings = config.interpret.emulationSettings
      completeTokenProbability = config.interpret.completeTokenProbability
      completeTokenSeed = config.interpret.completeTokenSeed
      useReordering = config.reorder.useReordering
      reorderingTitle = config.reorder.title
      featuresForReordering.addAll(config.reorder.features)
      evaluationTitle = config.reports.evaluationTitle
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
      language,
      outputDir,
      ActionsGeneration(
        evaluationRoots,
        CompletionStrategy(prefixStrategy, contextStrategy, emulateUser, completionGolf, filters)
      ),
      ActionsInterpretation(
        completionType,
        experimentGroup,
        sessionsLimit,
        completeTokenProbability,
        completeTokenSeed,
        emulationSettings,
        completionGolfSettings,
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
        sessionsFilters,
        comparisonFilters
      )
    )
  }
}
