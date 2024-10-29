// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.actions

import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import java.io.File

fun <T : EvaluationStrategy> List<EvaluationWorkspace>.buildMultipleEvaluationsConfig(
  strategySerializer: StrategySerializer<T>,
  title: String? = null,
): Config {
  val existingConfig = this.first().readConfig(strategySerializer)
  val projectPath = createTempProject()
  return Config.build {
    for (workspace in this@buildMultipleEvaluationsConfig) {
      val config = workspace.readConfig(strategySerializer)
      mergeFilters(config.reports.sessionsFilters)
      mergeComparisonFilters(config.reports.comparisonFilters)
    }
    actions = existingConfig.actions?.copy(
      projectPath = projectPath
    )
    strategy = existingConfig.strategy
    outputDir = existingConfig.outputDir
    title?.let { evaluationTitle = title }
  }
}

private fun createTempProject(): String {
  val dir = File("temp-project/.idea")
  dir.mkdirs()
  return dir.absolutePath
}
