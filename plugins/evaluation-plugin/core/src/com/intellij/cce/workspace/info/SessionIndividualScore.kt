package com.intellij.cce.workspace.info

data class SessionIndividualScore(
  val sessionId: String,
  val metricScores: Map<String, MutableList<Double>>,
  val additionalInfo: Map<String, MutableList<Any>>
)
