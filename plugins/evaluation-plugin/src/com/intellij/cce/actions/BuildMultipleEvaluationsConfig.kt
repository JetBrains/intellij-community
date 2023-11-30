// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.actions

import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace

fun <T : EvaluationStrategy> List<EvaluationWorkspace>.buildMultipleEvaluationsConfig(strategySerializer: StrategySerializer<T>): Config {
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
