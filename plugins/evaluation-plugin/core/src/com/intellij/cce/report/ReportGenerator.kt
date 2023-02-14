package com.intellij.cce.report

import com.intellij.cce.workspace.info.FileEvaluationInfo

interface ReportGenerator {
  val type: String
  fun generateFileReport(sessions: List<FileEvaluationInfo>)
}
