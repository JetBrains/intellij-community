package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.UndoableEvaluationStep
import com.intellij.cce.evaluation.features.CCEContextFeatureProvider
import com.intellij.cce.evaluation.features.CCEElementFeatureProvider
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.codeInsight.completion.ml.ContextFeatureProvider
import com.intellij.codeInsight.completion.ml.ElementFeatureProvider
import com.intellij.completion.ml.experiment.ExperimentInfo
import com.intellij.completion.ml.experiment.ExperimentStatus
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.stats.completion.sender.StatisticSender
import com.intellij.stats.completion.storage.FilePathProvider
import com.intellij.stats.completion.storage.UniqueFilesProvider
import java.io.File
import java.nio.file.Paths
import java.util.*

class SetupStatsCollectorStep(private val project: Project,
                              private val experimentGroup: Int?,
                              private val logLocationAndTextItem: Boolean,
                              private val isHeadless: Boolean) : UndoableEvaluationStep {
  companion object {
    private val LOG = Logger.getInstance(SetupStatsCollectorStep::class.java)
    private const val SEND_LOGS_REGISTRY = "completion.stats.send.logs"
    private const val STATS_COLLECTOR_ID = "com.intellij.stats.completion"
    private const val COLLECT_LOGS_HEADLESS_KEY = "completion.evaluation.headless"
    fun statsCollectorLogsDirectory(): String = Paths.get(PathManager.getSystemPath(), "completion-stats-data").toString()

    fun deleteLogs() {
      val logsDirectory = File(statsCollectorLogsDirectory())
      if (logsDirectory.exists()) {
        logsDirectory.deleteRecursively()
      }
    }

    fun isStatsCollectorEnabled(): Boolean = PluginManagerCore.getPlugin(PluginId.getId(STATS_COLLECTOR_ID))?.isEnabled ?: false
  }

  val serviceManager = ApplicationManager.getApplication() as ComponentManagerImpl
  val initFileProvider: FilePathProvider? = serviceManager.getService(FilePathProvider::class.java)
  val initStatisticSender: StatisticSender? = serviceManager.getService(StatisticSender::class.java)
  val initExperimentStatus: ExperimentStatus? = serviceManager.getService(ExperimentStatus::class.java)
  private var initSendLogsValue = false
  private var initCollectLogsInHeadlessValue = false
  private lateinit var elementFeatureProvider: CCEElementFeatureProvider
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
    try {
      initSendLogsValue = Registry.`is`(SEND_LOGS_REGISTRY)
      Registry.get(SEND_LOGS_REGISTRY).setValue(false)
    } catch (e: MissingResourceException) {
      LOG.warn("No registry for not sending logs", e)
    }
    if (isHeadless) {
      initCollectLogsInHeadlessValue = java.lang.Boolean.getBoolean(COLLECT_LOGS_HEADLESS_KEY)
      System.setProperty(COLLECT_LOGS_HEADLESS_KEY, "true")
    }
    val experimentStatus = object : ExperimentStatus {
      // it allows to collect logs from all sessions (need a more explicit solution in stats-collector)
      override fun forLanguage(language: Language): ExperimentInfo =
        ExperimentInfo(true,
                       version = experimentGroup ?: 0,
                       shouldRank = false,
                       shouldShowArrows = false,
                       shouldCalculateFeatures = true,
                       shouldLogElementFeatures = true)

      // it allows to ignore experiment info during ranking
      override fun isDisabled(): Boolean = true
      override fun disable() = Unit
    }
    serviceManager.replaceRegularServiceInstance(FilePathProvider::class.java, filesProvider)
    serviceManager.replaceRegularServiceInstance(StatisticSender::class.java, statisticSender)
    serviceManager.replaceRegularServiceInstance(ExperimentStatus::class.java, experimentStatus)
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
        if (initExperimentStatus != null)
          serviceManager.replaceRegularServiceInstance(ExperimentStatus::class.java, initExperimentStatus)
        try {
          Registry.get(SEND_LOGS_REGISTRY).setValue(initSendLogsValue)
        }
        catch (e: MissingResourceException) {
          LOG.warn("No registry for not sending logs", e)
        }
        if (isHeadless) {
          System.setProperty(COLLECT_LOGS_HEADLESS_KEY, initCollectLogsInHeadlessValue.toString())
        }
        LOG.runAndLogException { unregisterFeatureProviders() }
        return workspace
      }
    }
  }

  private fun registerFeatureProvidersIfNeeded() {
    contextFeatureProvider = CCEContextFeatureProvider(logLocationAndTextItem)
    ContextFeatureProvider.EP_NAME.addExplicitExtension(Language.ANY, contextFeatureProvider)
    if (!logLocationAndTextItem) return
    elementFeatureProvider = CCEElementFeatureProvider()
    ElementFeatureProvider.EP_NAME.addExplicitExtension(Language.ANY, elementFeatureProvider)
  }

  private fun unregisterFeatureProviders() {
    ContextFeatureProvider.EP_NAME.removeExplicitExtension(Language.ANY, contextFeatureProvider)
    if (!logLocationAndTextItem) return
    ElementFeatureProvider.EP_NAME.removeExplicitExtension(Language.ANY, elementFeatureProvider)
  }
}