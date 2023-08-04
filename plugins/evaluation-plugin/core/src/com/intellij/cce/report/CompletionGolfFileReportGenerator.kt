// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.metric.MatchedRatio
import com.intellij.cce.metric.MovesCount
import com.intellij.cce.metric.MovesCountNormalised
import com.intellij.cce.metric.TotalLatencyMetric
import com.intellij.cce.workspace.storages.FeaturesStorage
import com.intellij.cce.workspace.storages.FullLineLogsStorage

class CompletionGolfFileReportGenerator(
  filterName: String,
  comparisonFilterName: String,
  featuresStorages: List<FeaturesStorage>,
  fullLineStorages: List<FullLineLogsStorage>,
  dirs: GeneratorDirectories
) : BaseCompletionGolfFileReportGenerator(filterName, comparisonFilterName, featuresStorages, fullLineStorages, dirs) {

  override fun computeMetric(session: Session): Double = MovesCountNormalised().evaluate(listOf(session))

  override fun getLineStats(session: Session): List<String> {
    val movesCount = MovesCount().evaluate(listOf(session))
    val movesCountNormalised = MovesCountNormalised().evaluate(listOf(session))
    val totalLatency = TotalLatencyMetric().evaluate(listOf(session))

    val info = mutableListOf<String>().apply {
      add("${(movesCountNormalised * 100).format()}%".padEnd(4, ' '))
      add("$movesCount act")
      add("${(totalLatency / 1000).format()}s".padEnd(4, ' '))
    }

    return info
  }

  override fun getKindClass(lookup: Lookup, expectedText: String): String {
    val restText = expectedText.substring(lookup.offset)
    val matchedRatio = (MatchedRatio().computeSimilarity(lookup, restText) ?: 0.0) / restText.length
    return when {
      matchedRatio == 1.0 -> "cg-line"
      matchedRatio > 0.0 -> "cg-token"
      else -> "cg-none"
    }
  }

  override fun getThresholds(): List<BaseThreshold> = Threshold.values().toList()

  override fun getThresholdClass(value: Double?): String = value?.let {
    when {
      Threshold.EXCELLENT >= value -> Threshold.EXCELLENT.className
      Threshold.GOOD >= value -> Threshold.GOOD.className
      Threshold.SATISFACTORY >= value -> Threshold.SATISFACTORY.className
      Threshold.BAD >= value -> Threshold.BAD.className
      Threshold.VERY_BAD >= value -> Threshold.VERY_BAD.className
      else -> "stats-unknown"
    }
  } ?: "stats-unknown"

  companion object {
    private enum class Threshold(override val value: Double, override val className: String) : BaseThreshold {
      EXCELLENT(System.getenv("CG_THRESHOLD_EXCELLENT")?.toDouble() ?: 0.15, "stats-excellent"),
      GOOD(System.getenv("CG_THRESHOLD_GOOD")?.toDouble() ?: 0.25, "stats-good"),
      SATISFACTORY(System.getenv("CG_THRESHOLD_SATISFACTORY")?.toDouble() ?: 0.5, "stats-satisfactory"),
      BAD(System.getenv("CG_THRESHOLD_BAD")?.toDouble() ?: 0.8, "stats-bad"),
      VERY_BAD(System.getenv("CG_THRESHOLD_VERY_BAD")?.toDouble() ?: 1.0, "stats-very_bad");
    }
  }
}
