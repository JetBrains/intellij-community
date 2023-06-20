// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.metric.MetricInfo
import com.intellij.cce.workspace.info.FileErrorInfo
import java.nio.file.Path

interface FullReportGenerator : ReportGenerator {
  fun generateErrorReports(errors: List<FileErrorInfo>)
  fun generateGlobalReport(globalMetrics: List<MetricInfo>): Path
}

