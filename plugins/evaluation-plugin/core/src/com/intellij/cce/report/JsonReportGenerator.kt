// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.google.gson.GsonBuilder
import com.intellij.cce.metric.MetricInfo
import com.intellij.cce.workspace.info.FileErrorInfo
import com.intellij.cce.workspace.info.FileEvaluationInfo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.writeText

class JsonReportGenerator(
  outputDir: String,
  filterName: String,
  comparisonFilterName: String,
) : FullReportGenerator {
  override val type = "json"

  private val metricPerFile = mutableMapOf<String, List<MetricInfo>>()

  private val dir = Paths.get(outputDir, comparisonFilterName, type, filterName).also { Files.createDirectories(it) }

  override fun generateErrorReports(errors: List<FileErrorInfo>) = Unit

  override fun generateFileReport(sessions: List<FileEvaluationInfo>) {
    if (sessions.isEmpty()) return
    val fileInfo = sessions.first()

    metricPerFile[fileInfo.sessionsInfo.filePath] = sessions.map { it.metrics }.flatten()
  }

  override fun generateGlobalReport(globalMetrics: List<MetricInfo>): Path {
    return dir.resolve(metricsInfoName).also {
      it.writeText(gson.toJson(mapOf(
        "metrics" to mapOf(
          "global" to globalMetrics,
          "perFile" to metricPerFile
        ))))
    }
  }

  companion object {
    private const val metricsInfoName = "metrics.json"

    private val gson = GsonBuilder().apply {
      setPrettyPrinting()
      serializeNulls()
      disableHtmlEscaping()
      serializeSpecialFloatingPointValues()
    }.create()
  }
}
