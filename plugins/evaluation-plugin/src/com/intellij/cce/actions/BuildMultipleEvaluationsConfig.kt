package com.intellij.cce.actions

import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace

/**
 * TODO - it actually doesn't work
 */
fun<T : EvaluationStrategy> List<EvaluationWorkspace>.buildMultipleEvaluationsConfig(strategySerializer: StrategySerializer<T>): Config {
  val existingConfig = this.first().readConfig(strategySerializer)
  return Config.build(existingConfig.projectPath, existingConfig.language) {
    for (workspace in this@buildMultipleEvaluationsConfig) {
      val config = workspace.readConfig(strategySerializer)
      mergeFilters(config.reports.sessionsFilters)
      mergeComparisonFilters(config.reports.comparisonFilters)
    }
    strategy = existingConfig.strategy
    outputDir = existingConfig.outputDir
    evaluationTitle = "COMPARE_MULTIPLE"
  }
}