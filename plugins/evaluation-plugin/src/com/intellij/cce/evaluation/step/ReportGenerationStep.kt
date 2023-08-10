// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step


import com.intellij.cce.evaluable.EvaluableFeature
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluation.FilteredSessionsStorage
import com.intellij.cce.metric.MetricsEvaluator
import com.intellij.cce.report.*
import com.intellij.cce.util.Progress
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.cce.workspace.filter.CompareSessionsFilter
import com.intellij.cce.workspace.filter.CompareSessionsStorage
import com.intellij.cce.workspace.filter.CompareSessionsStorageImpl
import com.intellij.cce.workspace.filter.SessionsFilter
import com.intellij.cce.workspace.info.FileEvaluationInfo
import com.intellij.cce.workspace.info.FileSessionsInfo
import com.intellij.cce.workspace.storages.FileErrorsStorage
import com.intellij.cce.workspace.storages.SessionsStorage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.nio.file.Path

class ReportGenerationStep<T : EvaluationStrategy>(
  private val inputWorkspaces: List<EvaluationWorkspace>?,
  filters: List<SessionsFilter>,
  comparisonFilters: List<CompareSessionsFilter>,
  project: Project,
  private val feature: EvaluableFeature<T>
) : BackgroundEvaluationStep(project) {
  override val name: String = "Report generation"

  override val description: String = "Generation of HTML-report"

  private val sessionsFilters: List<SessionsFilter> = listOf(SessionsFilter.ACCEPT_ALL, *filters.toTypedArray())
  private val comparisonStorages: List<CompareSessionsStorage> = listOf(CompareSessionsStorage.ACCEPT_ALL) +
                                                                 if (inputWorkspaces?.size == 2) comparisonFilters.map {
                                                                   CompareSessionsStorageImpl(it)
                                                                 }
                                                                 else emptyList()

  override fun runInBackground(workspace: EvaluationWorkspace, progress: Progress): EvaluationWorkspace {
    val workspaces = inputWorkspaces ?: listOf(workspace)
    val configs = workspaces.map { it.readConfig(feature.getStrategySerializer()) }
    val evaluationTitles = configs.map { it.reports.evaluationTitle }
    val featuresStorages = workspaces.map { it.featuresStorage }
    val fullLineStorages = workspaces.map { it.fullLineLogsStorage }
    val iterationsCount = sessionsFilters.size * comparisonStorages.size
    val defaultMetrics = configs.firstOrNull()?.reports?.defaultMetrics
    var iteration = 0
    for (filter in sessionsFilters) {
      val sessionStorages = workspaces.map { FilteredSessionsStorage(filter, it.sessionsStorage) }
      val sessionFiles = matchSessions(evaluationTitles, sessionStorages)
      for (comparisonStorage in comparisonStorages) {
        if (progress.isCanceled()) {
          LOG.info("Generating reports is canceled by user. Done: $iteration/${iterationsCount}.")
          break
        }
        LOG.info("Start generating report for filter ${filter.name} ${comparisonStorage.reportName}. Done: $iteration/${iterationsCount}.")
        progress.setProgress(filter.name, "${filter.name} ${comparisonStorage.reportName} filter ($iteration/${iterationsCount})",
                             (iteration.toDouble() + 1) / iterationsCount)

        val dirs = GeneratorDirectories.create(workspace.reportsDirectory(), "html", filter.name, comparisonStorage.reportName)
        val reportGenerators = mutableListOf(
          HtmlReportGenerator(
            dirs,
            defaultMetrics,
            feature.getFileReportGenerator(filter.name, comparisonStorage.reportName, featuresStorages, fullLineStorages,
                                           dirs)
          ),
          JsonReportGenerator(
            workspace.reportsDirectory(),
            filter.name,
            comparisonStorage.reportName,
          )
        )
        if (ApplicationManager.getApplication().isUnitTestMode) reportGenerators.add(
          PlainTextReportGenerator(workspace.reportsDirectory(), filter.name))
        val reports = generateReport(
          reportGenerators,
          sessionFiles,
          sessionStorages,
          workspaces.map { it.errorsStorage },
          evaluationTitles,
          comparisonStorage
        )
        for (report in reports) {
          workspace.addReport(report.type, filter.name, comparisonStorage.reportName, report.path)
        }
        iteration++
      }
    }
    return workspace
  }

  data class SessionsInfo(val path: String, val sessionsPath: String, val evaluationType: String)
  data class ReportInfo(val type: String, val path: Path)

  private fun matchSessions(evaluationTitles: List<String>, sessionStorages: List<SessionsStorage>): Map<String, List<SessionsInfo>> {
    val sessionFiles: MutableMap<String, MutableList<SessionsInfo>> = mutableMapOf()
    if (evaluationTitles.toSet().size != evaluationTitles.size)
      throw IllegalStateException("Workspaces have same evaluation titles. Change evaluation title in config.json.")
    for ((index, storage) in sessionStorages.withIndex()) {
      for (pathsPair in storage.getSessionFiles()) {
        val sessionFile = sessionFiles.getOrPut(pathsPair.first) { mutableListOf() }
        sessionFile.add(SessionsInfo(pathsPair.first, pathsPair.second, evaluationTitles[index]))
      }
    }
    return sessionFiles
  }

  private fun generateReport(
    reportGenerators: List<FullReportGenerator>,
    sessionFiles: Map<String, List<SessionsInfo>>,
    sessionStorages: List<SessionsStorage>,
    errorStorages: List<FileErrorsStorage>,
    evaluationTitles: List<String>,
    comparisonStorage: CompareSessionsStorage,
  ): List<ReportInfo> {
    val title2evaluator = evaluationTitles.mapIndexed { index, title ->
      title to MetricsEvaluator.withMetrics(title, feature.getMetrics())
    }.toMap()
    for (sessionFile in sessionFiles.filter { it.value.size == sessionStorages.size }) {
      val fileEvaluations = mutableListOf<FileEvaluationInfo>()
      var filePath = ""
      var fileText = ""
      for (file in sessionFile.value) {
        val sessionsInfo = sessionStorages[evaluationTitles.indexOf(file.evaluationType)].getSessions(file.path)
        filePath = sessionsInfo.filePath
        fileText = sessionsInfo.text
        comparisonStorage.add(file.evaluationType, sessionsInfo.sessions)
      }
      for (file in sessionFile.value) {
        val sessionsEvaluation = FileSessionsInfo(filePath, fileText, comparisonStorage.get(file.evaluationType))
        val metricsEvaluation = title2evaluator.getValue(file.evaluationType).evaluate(
          sessionsEvaluation.sessions)
        fileEvaluations.add(FileEvaluationInfo(sessionsEvaluation, metricsEvaluation, file.evaluationType))
      }
      comparisonStorage.clear()
      reportGenerators.forEach { it.generateFileReport(fileEvaluations) }
    }
    for (errorsStorage in errorStorages) {
      reportGenerators.forEach { it.generateErrorReports(errorsStorage.getErrors()) }
    }

    val metrics = title2evaluator.values.map(MetricsEvaluator::result).flatten()

    return reportGenerators.map {
      ReportInfo(it.type, it.generateGlobalReport(metrics))
    }
  }
}
