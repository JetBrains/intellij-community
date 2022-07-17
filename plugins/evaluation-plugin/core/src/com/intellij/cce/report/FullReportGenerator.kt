package com.intellij.cce.report

import com.intellij.cce.metric.MetricInfo
import com.intellij.cce.workspace.info.FileErrorInfo
import java.nio.file.Path

interface FullReportGenerator : ReportGenerator {
  fun generateErrorReports(errors: List<FileErrorInfo>)
  fun generateGlobalReport(globalMetrics: List<MetricInfo>): Path
}
