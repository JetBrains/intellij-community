package com.intellij.cce.metric

import com.intellij.cce.workspace.info.SessionIndividualScore

data class MetricEvaluationResult(
  val overallScore: Number,
  val sessionIndividualScores: Map<String, SessionIndividualScore>
)
