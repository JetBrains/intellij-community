package com.intellij.cce.metric.util

import com.intellij.openapi.project.Project

interface CloudSemanticSimilarityCalculator {
  suspend fun calculateCosineSimilarity(
    project: Project,
    proposal: String,
    expectedText: String,
  ): Double
}