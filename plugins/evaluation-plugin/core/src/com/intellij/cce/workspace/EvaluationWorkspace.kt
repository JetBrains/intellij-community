// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace

import com.google.gson.Gson
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.fus.FusLogsSaver
import com.intellij.cce.workspace.storages.*
import com.intellij.cce.workspace.storages.storage.ActionsStorage
import com.intellij.cce.workspace.storages.storage.ActionsStorageFactory
import com.intellij.cce.workspace.storages.storage.getActionsStorageTypeFromEnv
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*

class EvaluationWorkspace private constructor(private val basePath: Path,
                                              statsLogsPath: Path) {
  companion object {
    private const val DEFAULT_REPORT_TYPE = "html"
    private val gson = Gson()
    private val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")

    fun open(workspaceDir: String, statsLogsPath: Path): EvaluationWorkspace {
      return EvaluationWorkspace(Paths.get(workspaceDir).toAbsolutePath(), statsLogsPath)
    }

    fun create(config: Config, statsLogsPath: Path): EvaluationWorkspace {
      val workspace = EvaluationWorkspace(Paths.get(config.outputDir).toAbsolutePath().resolve(formatter.format(Date())), statsLogsPath)
      workspace.writeConfig(config)
      return workspace
    }
  }

  private val sessionsDir = subdir("data")
  private val fullLineLogsDir = subdir("full-line-logs")
  private val featuresDir = subdir("features")
  private val actionsDir = subdir("actions")
  private val errorsDir = subdir("errors")
  private val reportsDir = subdir("reports")
  private val pathToConfig = path().resolve(ConfigFactory.DEFAULT_CONFIG_NAME)
  private val _reports: MutableMap<String, MutableMap<String, Path>> = mutableMapOf()

  val sessionsStorage: CompositeSessionsStorage = CompositeSessionsStorage(sessionsDir.toString())

  val actionsStorage: ActionsStorage = ActionsStorageFactory.create(actionsDir.toString(), getActionsStorageTypeFromEnv())

  val errorsStorage: FileErrorsStorage = FileErrorsStorage(errorsDir.toString())

  val statLogsSaver: StatLogsSaver = StatLogsSaver(statsLogsPath, subdir("logs"))

  val fusLogsSaver: LogsSaver = FusLogsSaver(subdir("fus-logs"))

  val featuresStorage: FeaturesStorage = FeaturesStorageImpl(featuresDir.toString())

  val fullLineLogsStorage: FullLineLogsStorage = FullLineLogsStorage(fullLineLogsDir.toString())

  override fun toString(): String = "Evaluation workspace: $basePath"

  fun saveMetadata() {
    sessionsStorage.saveMetadata()
    featuresStorage.saveMetadata()
    fullLineLogsStorage.saveMetadata()
  }

  fun reportsDirectory(): String = reportsDir.toString()

  fun path(): Path = basePath

  fun <T : EvaluationStrategy> readConfig(strategySerializer: StrategySerializer<T>): Config =
    ConfigFactory.load(pathToConfig, strategySerializer)

  fun saveAdditionalStats(name: String, stats: Map<String, Any>) {
    FileWriter(basePath.resolve("$name.json").toString()).use { it.write(gson.toJson(stats)) }
  }

  fun addReport(reportType: String, filterName: String, comparisonFilterName: String, reportPath: Path) {
    _reports.getOrPut(reportType) { mutableMapOf() }["$filterName $comparisonFilterName"] = reportPath
  }

  fun getReports(reportType: String = DEFAULT_REPORT_TYPE): Map<String, Path> = _reports.getOrDefault(reportType, emptyMap())

  private fun writeConfig(config: Config) = ConfigFactory.save(config, basePath)

  private fun subdir(name: String): Path {
    val directory = basePath.resolve(name)
    Files.createDirectories(directory)
    return directory
  }
}