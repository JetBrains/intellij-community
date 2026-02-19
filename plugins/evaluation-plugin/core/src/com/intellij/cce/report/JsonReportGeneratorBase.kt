// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.metric.MetricInfo
import com.intellij.cce.workspace.info.FileErrorInfo
import com.intellij.cce.workspace.info.FileEvaluationInfo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

abstract class JsonReportGeneratorBase(
  outputDir: String,
  filterName: String,
  comparisonFilterName: String,
  final override val type: String,
) : FullReportGenerator {
  protected val metricPerFile = mutableMapOf<String, List<MetricInfo>>()

  protected val dir: Path = Paths.get(outputDir, comparisonFilterName, type, filterName).also { Files.createDirectories(it) }

  override fun generateErrorReports(errors: List<FileErrorInfo>) = Unit

  override fun generateFileReport(sessions: List<FileEvaluationInfo>) {
    if (sessions.isEmpty()) return
    val fileInfo = sessions.first()

    metricPerFile[fileInfo.sessionsInfo.filePath] = sessions.map { it.metrics }.flatten()
  }
}