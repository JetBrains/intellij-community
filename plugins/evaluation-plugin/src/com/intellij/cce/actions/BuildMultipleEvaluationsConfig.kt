package com.intellij.cce.actions

import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace

fun List<EvaluationWorkspace>.buildMultipleEvaluationsConfig(): Config {
  val existingConfig = this.first().readConfig()
  return Config.build(existingConfig.projectPath, existingConfig.language) {
    for (workspace in this@buildMultipleEvaluationsConfig) {
      val config = workspace.readConfig()
      mergeFilters(config.reports.sessionsFilters)
      mergeComparisonFilters(config.reports.comparisonFilters)
    }
    evaluationTitle = "COMPARE_MULTIPLE"
  }
}