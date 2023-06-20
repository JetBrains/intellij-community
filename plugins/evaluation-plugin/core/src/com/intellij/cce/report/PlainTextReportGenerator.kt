// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.metric.MetricInfo
import com.intellij.cce.workspace.info.FileErrorInfo
import com.intellij.cce.workspace.info.FileEvaluationInfo
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class PlainTextReportGenerator(outputDir: String, filterName: String) : FullReportGenerator {
  companion object {
    private const val globalReportName: String = "report.txt"
  }

  override val type: String = "plain"
  private val filterDir = Paths.get(outputDir, type, filterName)

  init {
    Files.createDirectories(filterDir)
  }

  override fun generateFileReport(sessions: List<FileEvaluationInfo>) = Unit
  override fun generateErrorReports(errors: List<FileErrorInfo>) = Unit

  override fun generateGlobalReport(globalMetrics: List<MetricInfo>): Path {
    val reportPath = Paths.get(filterDir.toString(), globalReportName)
    FileWriter(reportPath.toString()).use { writer ->
      writer.write(globalMetrics
                     .filter { !it.name.contains("latency", ignoreCase = true) }
                     .joinToString("\n") { "${it.evaluationType} ${it.name}: ${"%.3f".format(Locale.US, it.value)}" })
    }
    return reportPath
  }
}
