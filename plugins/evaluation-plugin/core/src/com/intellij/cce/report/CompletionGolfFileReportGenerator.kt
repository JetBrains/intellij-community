package com.intellij.cce.report

import com.intellij.cce.actions.selectedWithoutPrefix
import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.core.SuggestionKind
import com.intellij.cce.metric.*
import com.intellij.cce.workspace.info.FileEvaluationInfo
import com.intellij.cce.workspace.storages.FeaturesStorage
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.apache.commons.lang.StringEscapeUtils

class CompletionGolfFileReportGenerator(
  filterName: String,
  comparisonFilterName: String,
  featuresStorages: List<FeaturesStorage>,
  dirs: GeneratorDirectories
) : FileReportGenerator(featuresStorages, dirs, filterName, comparisonFilterName) {

  override fun getHtml(fileEvaluations: List<FileEvaluationInfo>, fileName: String, resourcePath: String, text: String): String {
    return createHTML().body {
      if (fileEvaluations.size > 1) {
        div {
          label("labelText") { +"For session:" }
        }
        div("tab") {
          fileEvaluations.forEachIndexed { index, info ->
            tabButton(info.evaluationType, index == 0)
          }
        }
      }
      div {
        label("labelText") { +"With delimiter:" }
        select("delimiter-pick") {
          delOption("cg-delimiter-integral", "&int;")
          delOption("cg-delimiter-box-small", "&#10073;")
          delOption("cg-delimiter-box-big", "&#10074;")
          delOption("cg-delimiter-underscore", "_")
          delOption("cg-delimiter-none", "none")
        }
      }
      code("cg cg-delimiter-integral") {
        table {
          fileEvaluations.forEach {
            getTable(it, text, it.evaluationType)
          }
        }
      }
      script { src = "../res/script.js" }
      script { +"isCompletionGolf = true" }
    }
  }

  private fun SELECT.delOption(id: String, delCode: String) {
    option {
      value = id
      unsafe { raw(delCode) }
    }
  }

  private fun FlowContent.tabButton(title: String, isDefault: Boolean = false) {
    button(classes = "tablinks") {
      if (isDefault) {
        id = "defaultTabOpen"
      }
      onClick = "showSession(event, '$title')"
      +title
    }
  }

  private fun TABLE.getTable(info: FileEvaluationInfo, fullText: String, evaluationId: String) {
    tbody("evalContent") {
      id = evaluationId
      var offset = 0
      var lineNumbers = 0
      info.sessionsInfo.sessions.forEach { session ->
        val text = fullText.substring(offset, session.offset)
        val tab = text.lines().last().dropWhile { it == '\n' }
        val tail = fullText.drop(session.offset + session.expectedText.length).takeWhile { it != '\n' }

        lineNumbers += defaultText(text, lineNumbers)

        tr {
          td("line-numbers") {
            attributes["data-line-numbers"] = lineNumbers.toString()
          }
          td("code-line") {
            val metricsPerSession = MetricsEvaluator.withDefaultMetrics(info.evaluationType, true).evaluate(listOf(session))
            prepareLine(session, tab, metricsPerSession)
            if (tail.isNotEmpty()) {
              pre("ib") { +tail }
            }
          }
        }
        offset = session.offset + session.expectedText.length
        lineNumbers++
      }

      if (offset != fullText.length) {
        defaultText(fullText.substring(offset, fullText.length), lineNumbers)
      }
    }
  }

  private fun FlowContent.prepareLine(session: Session, tab: String, metrics: List<MetricInfo>) {
    val expectedText = session.expectedText
    val lookups = session.lookups

    var offset = 0

    div("line-code") {
      pre("ib") { +StringEscapeUtils.escapeHtml(tab) }
      lookups.forEachIndexed { index, lookup ->
        offset = prepareSpan(expectedText, lookup, session.id, index, offset)
      }
    }

    div("line-stats") {
      val movesAction = metrics.findByName(MovesCount.NAME)
      val movesNormalisedAction = metrics.findByName(MovesCountNormalised.NAME)
      val totalLatencyAction = metrics.findByName(TotalLatencyMetric.NAME)

      val info = mutableListOf<String>()
      if (movesNormalisedAction != null) info.add("%.2f".format(movesNormalisedAction.value * 100) + "%")
      if (movesAction != null) info.add("${movesAction.value.toInt()} act")
      if (totalLatencyAction != null) info.add("%.2f".format(totalLatencyAction.value / 1000) + "s")

      if (info.isNotEmpty()) {
        i {
          pre {
            +StringEscapeUtils.escapeHtml(
              info.joinToString(separator = ",\t", prefix = "    #  ")
            )
          }
        }
      }
    }
  }

  private fun FlowContent.prepareSpan(
    expectedText: String,
    lookup: Lookup,
    uuid: String,
    columnId: Int,
    offset: Int,
  ): Int {
    val kinds = lookup.suggestions.map { suggestion -> suggestion.kind }
    val kindClass = when {
      SuggestionKind.LINE in kinds -> "cg-line"
      SuggestionKind.TOKEN in kinds -> "cg-token"
      else -> "cg-none"
    }

    val text = lookup.selectedWithoutPrefix() ?: expectedText[offset].toString()

    span("completion $kindClass delimiter") {
      attributes["data-cl"] = "$columnId"
      attributes["data-id"] = uuid
      +text
    }
    return offset + text.length
  }

  private fun TBODY.defaultText(text: String, lineNumbers: Int): Int {
    return text.lines()
      .drop(1).dropLast(1)
      .onEachIndexed { i, line ->
        tr {
          td("line-numbers") {
            attributes["data-line-numbers"] = (lineNumbers + i).toString()
          }
          td("code-line") {
            style = "white-space: normal;"
            pre("ib") { +line }
          }
          lineNumbers + i
        }
      }.size
  }

  companion object {
    fun List<MetricInfo>.findByName(name: String): MetricInfo? {
      //format name same as in MetricInfo$name
      val formattedName = name.filter { it.isLetterOrDigit() }
      return find { it.name == formattedName }
    }
  }
}
