// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.UndoableEvaluationStep
import com.intellij.cce.evaluation.features.CCEContextFeatureProvider
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.stats.completion.sender.StatisticSender
import com.intellij.stats.completion.storage.FilePathProvider
import com.intellij.stats.completion.storage.UniqueFilesProvider
import java.nio.file.Path
import java.nio.file.Paths

class SetupStatsCollectorStep(private val experimentGroup: Int?,
                              private val logLocationAndTextItem: Boolean) : UndoableEvaluationStep {
  companion object {
    private val LOG = thisLogger()
    private const val SEND_LOGS_KEY = "completion.stats.send.logs"
    private const val STATS_COLLECTOR_ID = "com.intellij.stats.completion"
    private const val COLLECT_LOGS_HEADLESS_KEY = "completion.evaluation.headless"
    val statsCollectorLogsDirectory: Path = Paths.get(PathManager.getSystemPath(), "completion-stats-data")

    fun deleteLogs() {
      val logsDirectory = statsCollectorLogsDirectory.toFile()
      if (logsDirectory.exists()) {
        logsDirectory.deleteRecursively()
      }
    }

    fun isStatsCollectorEnabled(): Boolean = PluginManagerCore.getPlugin(PluginId.getId(STATS_COLLECTOR_ID))?.isEnabled ?: false
  }

  val serviceManager = ApplicationManager.getApplication() as ComponentManagerEx
  val initFileProvider: FilePathProvider? = serviceManager.getService(FilePathProvider::class.java)
  val initStatisticSender: StatisticSender? = serviceManager.getService(StatisticSender::class.java)
  private var initSendLogsValue = false
  private var initCollectLogsInHeadlessValue = false
  private lateinit var contextFeatureProvider: CCEContextFeatureProvider

  override val name: String = "Setup Stats Collector step"
  override val description: String = "Configure plugin Stats Collector if needed"

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace? {
    if (!isStatsCollectorEnabled()) {
      println("Stats Collector plugin isn't installed. Install it, if you want to save completion logs or features.")
      return null
    }

    deleteLogs()
    val filesProvider = object : UniqueFilesProvider("chunk", PathManager.getSystemPath(), "completion-stats-data", 10) {
      override fun cleanupOldFiles() = Unit
    }
    val statisticSender = object : StatisticSender {
      override fun sendStatsData(url: String) = Unit
    }
    initSendLogsValue = java.lang.Boolean.parseBoolean(System.getProperty(SEND_LOGS_KEY, "true"))
    System.setProperty(SEND_LOGS_KEY, "false")
    initCollectLogsInHeadlessValue = java.lang.Boolean.getBoolean(COLLECT_LOGS_HEADLESS_KEY)
    System.setProperty(COLLECT_LOGS_HEADLESS_KEY, "true")
    serviceManager.replaceRegularServiceInstance(FilePathProvider::class.java, filesProvider)
    serviceManager.replaceRegularServiceInstance(StatisticSender::class.java, statisticSender)
    LOG.runAndLogException { registerFeatureProvidersIfNeeded() }
    return workspace
  }

  override fun undoStep(): UndoableEvaluationStep.UndoStep {
    return object : UndoableEvaluationStep.UndoStep {
      override val name: String = "Undo setup Stats Collector step"
      override val description: String = "Return default behaviour of Stats Collector plugin"

      override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace {
        if (initFileProvider != null)
          serviceManager.replaceRegularServiceInstance(FilePathProvider::class.java, initFileProvider)
        if (initStatisticSender != null)
          serviceManager.replaceRegularServiceInstance(StatisticSender::class.java, initStatisticSender)
        System.setProperty(SEND_LOGS_KEY, initSendLogsValue.toString())
        System.setProperty(COLLECT_LOGS_HEADLESS_KEY, initCollectLogsInHeadlessValue.toString())
        LOG.runAndLogException { unregisterFeatureProviders() }
        return workspace
      }
    }
  }

  private fun registerFeatureProvidersIfNeeded() {
    contextFeatureProvider = CCEContextFeatureProvider(logLocationAndTextItem)
    ContextFeatureProvider.EP_NAME.addExplicitExtension(Language.ANY, contextFeatureProvider)
  }

  private fun unregisterFeatureProviders() {
    ContextFeatureProvider.EP_NAME.removeExplicitExtension(Language.ANY, contextFeatureProvider)
  }
}