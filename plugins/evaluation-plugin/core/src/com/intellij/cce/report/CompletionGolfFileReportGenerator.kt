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
import java.text.DecimalFormat

class CompletionGolfFileReportGenerator(
  filterName: String,
  comparisonFilterName: String,
  featuresStorages: List<FeaturesStorage>,
  dirs: GeneratorDirectories
) : FileReportGenerator(featuresStorages, dirs, filterName, comparisonFilterName) {

  override fun getHtml(fileEvaluations: List<FileEvaluationInfo>, fileName: String, resourcePath: String, text: String): String {
    return createHTML().body {
      div("cg") {
        div {
          style = "display: flex; gap: 12px;"
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
          div("thresholds") {
            label("labelText") { +"Threshold grades:" }
            Threshold.values().forEach {
              val statsClass = Threshold.getClass(it)
              span(statsClass) {
                button(classes = "stats-value") {
                  onClick = "invertRows(event, '$statsClass')"
                  +((it.value * 100).format() + "%")
                }
              }
            }
          }
          if (fileEvaluations.size > 1) {
            div("cg-evaluations") {
              label("labelText") { +"Evaluations:" }
              fileEvaluations.forEachIndexed { i, info ->
                span("cg-evaluation-title") { +"${i + 1}. ${info.evaluationType}" }
              }
            }
          }
        }
        code("cg-file cg-delimiter-integral") {
          table {
            getTable(fileEvaluations, text)
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

  private fun TABLE.getTable(infos: List<FileEvaluationInfo>, fullText: String) {
    tbody("evalContent") {
      var offset = 0
      var lineNumbers = 0
      val firstInfo = infos.first()
      for (sessionIndex in firstInfo.sessionsInfo.sessions.indices) {
        val session = firstInfo.sessionsInfo.sessions[sessionIndex]
        val text = fullText.substring(offset, session.offset)
        val tab = text.lines().last().dropWhile { it == '\n' }
        val tail = fullText.drop(session.offset + session.expectedText.length).takeWhile { it != '\n' }

        lineNumbers += defaultText(text, lineNumbers)

        val curSessions = infos.map { it.sessionsInfo.sessions[sessionIndex] }
        val movesNormalizedAll = curSessions.map { MovesCountNormalised().evaluate(listOf(it)) }
        for ((curSession, movesNormalised) in curSessions.zip(movesNormalizedAll)) {
          var rowClasses = Threshold.getClass(movesNormalised)
          if (curSessions.size > 1 && movesNormalizedAll.distinct().size == 1) {
            rowClasses = "$rowClasses duplicate"
          }
          tr(rowClasses) {
            td("line-numbers") {
              attributes["data-line-numbers"] = lineNumbers.toString()
            }
            td("code-line") {
              prepareLine(curSession, tab, movesNormalised)
              if (tail.isNotEmpty()) {
                pre("ib") { +tail }
              }
            }
          }
        }
        if (curSessions.size > 1) {
          tr("tr-delimiter") {
            td()
            td("line-code") { hr() }
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

  private fun FlowContent.prepareLine(session: Session, tab: String, movesNormalised: Double) {
    val expectedText = session.expectedText
    val lookups = session.lookups

    var offset = 0

    div("line-code") {
      pre("ib") { +StringEscapeUtils.escapeHtml(tab) }
      lookups.forEachIndexed { i, lookup ->
        val currentChar = expectedText[offset]
        val delimiter = mutableListOf<String>().apply {
          if (lookups.size > 1 && i != 0) {
            add("delimiter")
          }
          if (currentChar.isWhitespace()) {
            consumer.onTagContentUnsafe { +currentChar.toString() }
            offset++
            add("delimiter-pre")
          }
        }.joinToString(" ")
        offset = prepareSpan(expectedText, lookup, session.id, i, offset, delimiter)
      }
    }

    div("line-stats") {
      val movesCount = MovesCount().evaluate(listOf(session))
      val totalLatency = TotalLatencyMetric().evaluate(listOf(session))

      val info = mutableListOf<String>().apply {
        add((movesNormalised * 100).format() + "%")
        add("$movesCount act")
        add((totalLatency / 1000).format() + "s")
      }

      if (info.isNotEmpty()) {
        i {
          style = "display: flex;"
          pre("no-select") { +"    #  " }
          pre("stats-value") {
            style = "padding-inline: 4px;"
            +StringEscapeUtils.escapeHtml(
              info.joinToString(separator = "\t", prefix = "", postfix = "\t") {
                it.padEnd(4, ' ')
              }
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
    delimiter: String = "",
  ): Int {
    val kinds = lookup.suggestions.map { suggestion -> suggestion.kind }
    val kindClass = when {
      SuggestionKind.LINE in kinds -> "cg-line"
      SuggestionKind.TOKEN in kinds -> "cg-token"
      else -> "cg-none"
    }

    val text = lookup.selectedWithoutPrefix() ?: expectedText[offset].toString()

    span("completion $kindClass $delimiter") {
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

  private fun Double.format() = DecimalFormat("0.##").format(this)

  companion object {
    private enum class Threshold(val value: Double) {
      EXCELLENT(System.getenv("CG_THRESHOLD_EXCELLENT")?.toDouble() ?: 0.15),
      GOOD(System.getenv("CG_THRESHOLD_GOOD")?.toDouble() ?: 0.25),
      SATISFACTORY(System.getenv("CG_THRESHOLD_SATISFACTORY")?.toDouble() ?: 0.5),
      BAD(System.getenv("CG_THRESHOLD_BAD")?.toDouble() ?: 0.8),
      VERY_BAD(System.getenv("CG_THRESHOLD_VERY_BAD")?.toDouble() ?: 1.0);

      operator fun Double.compareTo(t: Threshold) = compareTo(t.value)
      operator fun compareTo(d: Double) = value.compareTo(d)

      companion object {
        fun getClass(threshold: Threshold) = getClass(threshold.value)
        fun getClass(value: Double?) = value?.let {
          when {
            EXCELLENT >= value -> "stats-excellent"
            GOOD >= value -> "stats-good"
            SATISFACTORY >= value -> "stats-satisfactory"
            BAD >= value -> "stats-bad"
            VERY_BAD >= value -> "stats-very_bad"
            else -> "stats-unknown"
          }
        } ?: "stats-unknown"
      }
    }
  }
}
