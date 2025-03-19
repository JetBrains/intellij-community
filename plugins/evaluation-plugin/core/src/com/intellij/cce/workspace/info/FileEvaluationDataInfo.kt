package com.intellij.cce.workspace.info

data class FileEvaluationDataInfo(
  val projectName: String,
  val filePath: String,
  val sessionIndividualScores: List<SessionIndividualScore>
)
