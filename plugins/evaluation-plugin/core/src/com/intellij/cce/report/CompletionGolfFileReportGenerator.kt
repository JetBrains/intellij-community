package com.intellij.cce.report

import com.intellij.cce.actions.CompletionGolfEmulation
import com.intellij.cce.actions.selectedWithoutPrefix
import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.core.SuggestionKind
import com.intellij.cce.metric.MovesCount
import com.intellij.cce.metric.MovesCountNormalised
import com.intellij.cce.metric.PerfectLine
import com.intellij.cce.metric.TotalLatencyMetric
import com.intellij.cce.workspace.info.FileEvaluationInfo
import com.intellij.cce.workspace.storages.FeaturesStorage
import com.intellij.cce.workspace.storages.FullLineLogsStorage
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import java.io.File
import java.nio.file.Path
import java.text.DecimalFormat

class CompletionGolfFileReportGenerator(
  private val settings: CompletionGolfEmulation.Settings,
  filterName: String,
  comparisonFilterName: String,
  featuresStorages: List<FeaturesStorage>,
  private val fullLineStorages: List<FullLineLogsStorage>,
  dirs: GeneratorDirectories
) : FileReportGenerator(featuresStorages, dirs, filterName, comparisonFilterName) {

  override fun createHead(head: HEAD, reportTitle: String, resourcePath: Path) {
    super.createHead(head, reportTitle, resourcePath)
    with(head) {
      script {
        type = "module"
        src = "../res/index-v2.js?v=" + System.currentTimeMillis()
      }
      link {
        rel = "stylesheet"
        href = "../res/index-v2.css?v=" + System.currentTimeMillis()
      }
    }
  }

  override fun getHtml(fileEvaluations: List<FileEvaluationInfo>, fileName: String, resourcePath: String, text: String): String {
    return createHTML().body {
      div("cg") {
        div {
          style = "display: flex; gap: 12px;"
          div {
            a(classes = "v2-switcher") {
              onClick = "enableV2()"
              button {
                type = ButtonType.button
                +"v2 view"
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
          div("cg-perfect-line") {
            span { +perfectLineSign }
            label("labelText") { +" - perfect line" }
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
      script {
        +"""
          function enableV2() {
              const urlParams = new URLSearchParams(window.location.search);
              urlParams.set('v2', 'true');
              window.location.search = urlParams;
          }
        """.trimIndent()
      }
    }
  }

  override fun processStorages(fileInfos: List<FileEvaluationInfo>, resourceFile: File) {
    super.processStorages(fileInfos, resourceFile)
    for ((storageIndex, storage2info) in fullLineStorages.zip(fileInfos).withIndex()) {
      resourceFile.appendText("fullLineLog.push({});\n")
      val log = storage2info.first.getLog(storage2info.second.sessionsInfo.filePath) ?: continue
      val offset2json = mutableMapOf<Int, String>()
      for (line in log.lines()) {
        val offset = offsetRegex.find(line)?.destructured?.component1()?.toIntOrNull() ?: continue
        offset2json[offset] = line
      }
      for (session in storage2info.second.sessionsInfo.sessions) {
        for (lookup in session.lookups) {
          val offset = session.offset + lookup.offset
          val json = offset2json[offset] ?: continue
          resourceFile.appendText("fullLineLog[$storageIndex][$offset] = `${zipJson(json)}`;\n")
        }
      }
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
      if (firstInfo.sessionsInfo.sessions.isEmpty()) return@tbody
      val maxLineLength = firstInfo.sessionsInfo.sessions.maxOf { it.expectedText.length }
      for (sessionIndex in firstInfo.sessionsInfo.sessions.indices) {
        val session = firstInfo.sessionsInfo.sessions[sessionIndex]
        val text = fullText.substring(offset, session.offset)

        if (text.isNotEmpty()) {
          lineNumbers += defaultText(text, lineNumbers)
        }

        val curSessions = infos.map { it.sessionsInfo.sessions[sessionIndex] }
        val movesNormalizedAll = curSessions.map { MovesCountNormalised().evaluate(listOf(it)) }
        for ((evaluationIndex, session2moves) in curSessions.zip(movesNormalizedAll).withIndex()) {
          var rowClasses = Threshold.getClass(session2moves.second)
          if (curSessions.size > 1 && movesNormalizedAll.distinct().size == 1) {
            rowClasses = "$rowClasses duplicate"
          }
          tr(rowClasses) {
            td("line-numbers") {
              attributes["data-line-numbers"] = lineNumbers.toString()
            }
            td("code-line") {
              prepareLine(session2moves.first, session2moves.second, evaluationIndex, maxLineLength)
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

  private fun FlowContent.prepareLine(session: Session, movesNormalised: Double, evaluationIndex: Int, maxLineLength: Int) {
    val expectedText = session.expectedText
    val lookups = session.lookups
    var offset = 0

    div("line-code") {
      style = "min-width: calc(7.8 * ${maxLineLength}px);"
      lookups.forEachIndexed { i, lookup ->
        if (lookup.offset != offset) {
          span("code-span") { +expectedText.substring(offset, lookup.offset) }
          offset = lookup.offset
        }
        val delimiter = mutableListOf<String>().apply {
          if (lookups.size > 1 && i != 0) {
            add("delimiter")
          }
        }.joinToString(" ")
        offset = prepareSpan(expectedText, lookup, session.id, i, offset, session.offset + lookup.offset, evaluationIndex, delimiter)
      }
      if (expectedText.length != offset) {
        span("code-span") { +expectedText.substring(offset) }
      }
    }

    div("line-stats") {
      val movesCount = MovesCount().evaluate(listOf(session))
      val totalLatency = TotalLatencyMetric().evaluate(listOf(session))
      val isPerfectLine = PerfectLine().evaluate(listOf(session)) == 1

      val info = mutableListOf<String>().apply {
        add("${(movesNormalised * 100).format()}%".padEnd(4, ' '))
        add("$movesCount act")
        add("${(totalLatency / 1000).format()}s".padEnd(4, ' '))
        add(if (isPerfectLine) perfectLineSign else "")
      }

      i {
        style = "display: flex;"
        pre("no-select") { +"    #  " }
        pre("stats-value") {
          style = "padding-inline: 4px;"
          +info.joinToString(separator = "\t", prefix = "", postfix = "\t")
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
    offsetInFile: Int,
    evaluationIndex: Int,
    delimiter: String = "",
  ): Int {
    val kinds = lookup.suggestions.map { suggestion -> suggestion.kind }
    val kindClass = when {
      SuggestionKind.LINE in kinds -> "cg-line"
      SuggestionKind.TOKEN in kinds -> "cg-token"
      else -> "cg-none"
    }

    val text = settings.isBenchmark.takeUnless { it }?.let { lookup.selectedWithoutPrefix() } ?: expectedText[offset].toString()

    span("code-span completion $kindClass $delimiter") {
      attributes["data-cl"] = "$columnId"
      attributes["data-id"] = uuid
      attributes["data-offset"] = offsetInFile.toString()
      attributes["data-evaluation-id"] = evaluationIndex.toString()
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
    const val perfectLineSign: String = "\uD83C\uDF89" // :tada emoji:
    private val offsetRegex = "\"offset\":([0-9]+),".toRegex()

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
