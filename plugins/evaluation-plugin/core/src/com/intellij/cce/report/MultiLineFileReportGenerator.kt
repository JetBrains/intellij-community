// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.core.Session
import com.intellij.cce.metric.CharFScoreFirstLine
import com.intellij.cce.metric.Metric
import com.intellij.cce.metric.RelaxedExactMatchOnlyAlphanum
import com.intellij.cce.metric.TotalLatencyMetric
import com.intellij.cce.workspace.storages.FeaturesStorage
import kotlinx.html.*
import kotlin.math.roundToInt

class MultiLineFileReportGenerator(
  filterName: String,
  comparisonFilterName: String,
  featuresStorages: List<FeaturesStorage>,
  dirs: GeneratorDirectories
) : BasicFileReportGenerator(filterName, comparisonFilterName, featuresStorages, dirs) {

  override fun textToInsert(session: Session) = session.expectedText.lines().first()

  override fun codeContainer(containerDiv: DIV, text: String, sessions: List<List<Session>>, lookupOrder: Int) = containerDiv.apply {
    attributes["class"] += " multiline"
    div {
      id = "metrics-column"
      val metricsValues2Color = MutableList(text.lines().size) { " " to "" }
      sessions.flatten().forEach { metricsValues2Color[text.take(it.offset).lines().size - 1] = getSessionMetricsAndColor(it) }
      metricsValues2Color.forEach {
        div {
          style = "background-color: ${it.second}"
          pre("metrics-values") { +it.first }
        }
      }
    }
    super.codeContainer(this, text, sessions, lookupOrder)
  }

  private fun Metric.evaluateAndDisplay(session: Session): String {
    val score = evaluate(listOf(session)).toDouble().takeIf { !it.isNaN() } ?: 0.0
    return (score * 100).roundToInt().toString()
  }

  private fun getSessionMetricsAndColor(session: Session) = with(session) {
    listOf(
      "${PRIMARY_METRIC.evaluateAndDisplay(session)}%",
      "${SECONDARY_METRIC.evaluateAndDisplay(session)}%",
      "${formatDouble((evaluate(TotalLatencyMetric()) / 1000))}s"
    ).joinToString("  ", transform = { it.padEnd(4) }) to color()
  }

  private fun Session.evaluate(metric: Metric) = metric.evaluate(listOf(this)).toDouble()

  private fun Session.color() = "hsl(${(evaluate(PRIMARY_METRIC) * PRIMARY_COLORING_SCALER).roundToInt()}, 100%, 75%)"

  companion object {
    private val PRIMARY_METRIC = CharFScoreFirstLine()
    private const val PRIMARY_COLORING_SCALER = 120

    private val SECONDARY_METRIC = RelaxedExactMatchOnlyAlphanum()
  }
}
