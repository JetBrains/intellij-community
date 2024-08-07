// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.workspace.info.FileEvaluationInfo
import com.intellij.cce.workspace.storages.FeaturesStorage
import com.intellij.cce.workspace.storages.FullLineLogsStorage
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import java.io.File
import java.nio.file.Path

abstract class BaseCompletionGolfFileReportGenerator(
  filterName: String,
  comparisonFilterName: String,
  featuresStorages: List<FeaturesStorage>,
  private val fullLineStorages: List<FullLineLogsStorage>,
  dirs: GeneratorDirectories
) : FileReportGenerator(featuresStorages, dirs, filterName, comparisonFilterName) {

  override val scripts: List<Resource> = listOf(Resource("/script.js", "../res/script.js"))

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
              delOption("cg-delimiter-none", "none")
              delOption("cg-delimiter-integral", "&int;")
              delOption("cg-delimiter-box-small", "&#10073;")
              delOption("cg-delimiter-box-big", "&#10074;")
              delOption("cg-delimiter-underscore", "_")
            }
          }
          div("red-code") {
            label("labelText") { +"Filters check " }
            span("stats-absent") { +"skipped" }
          }
          div("wrong-filters") {
            label("labelText") { +"Highlight wrong filters: " }
            select {
              id = "wrong-filters"
              option {
                value = "no"
                label = "no"
              }
              option {
                value = "raw-filter"
                label = "raw"
              }
              option {
                value = "analyzed-filter"
                label = "analyzed"
              }
            }
          }
          div("model-skipped") {
            label("labelText") { +"Highlight skipped by model: " }
            select {
              id = "model-skipped"
              option {
                value = "no"
                label = "no"
              }
              option {
                value = "trigger-skipped"
                label = "trigger"
              }
              option {
                value = "filter-skipped"
                label = "filter"
              }
            }
          }
          div("thresholds") {
            label("labelText") { +"Threshold grades:" }
            getThresholds().forEach {
              val statsClass = it.className
              span(statsClass) {
                button(classes = "stats-value") {
                  onClick = "invertRows(event, '$statsClass')"
                  +(formatDouble((it.value * 100)) + "%")
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
        code("cg-file cg-delimiter-none") {
          table {
            getTable(fileEvaluations, text)
          }
        }
      }
      for (resource in scripts){
        script { src = resource.destinationPath }
      }
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
        val metricValuesAll = curSessions.map { computeMetric(it) }
        for ((evaluationIndex, session2metricValue) in curSessions.zip(metricValuesAll).withIndex()) {
          var rowClasses = getThresholdClass(session2metricValue.second)
          if (curSessions.size > 1 && metricValuesAll.distinct().size == 1) {
            rowClasses = "$rowClasses duplicate"
          }
          tr(rowClasses) {
            td("line-numbers") {
              attributes["data-line-numbers"] = lineNumbers.toString()
            }
            td("code-line") {
              prepareLine(session2metricValue.first, evaluationIndex, maxLineLength)
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

  private fun FlowContent.prepareLine(session: Session, evaluationIndex: Int, maxLineLength: Int) {
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

    if (session.lookups.isNotEmpty()) {
      div("line-stats") {
        i {
          style = "display: flex;"
          pre("no-select") { +"    #  " }
          pre("stats-value") {
            style = "padding-inline: 4px;"
            +getLineStats(session).joinToString(separator = "\t", prefix = "", postfix = "\t")
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
    offsetInFile: Int,
    evaluationIndex: Int,
    delimiter: String = "",
  ): Int {
    val text = expectedText[offset].toString()

    span("code-span session ${getKindClass(lookup, expectedText)} ${getFilterCheckClass(lookup, expectedText)} " +
         "${getSkippedByModelClass(lookup, expectedText)} $delimiter") {
      attributes["data-cl"] = "$columnId"
      attributes["data-id"] = uuid
      attributes["data-offset"] = offsetInFile.toString()
      attributes["data-evaluation-id"] = evaluationIndex.toString()
      id = "$uuid $columnId"
      +text
    }
    return offset + text.length
  }

  private fun TBODY.defaultText(text: String, lineNumbers: Int): Int {
    val shouldDropFirst = text.lines().firstOrNull()?.isEmpty() ?: false
    return text.lines()
      .drop(if (shouldDropFirst) 1 else 0).dropLast(1)
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

  protected abstract fun computeMetric(session: Session): Double

  protected abstract fun getLineStats(session: Session): List<String>

  protected abstract fun getKindClass(lookup: Lookup, expectedText: String): String

  protected abstract fun getFilterCheckClass(lookup: Lookup, expectedText: String): String

  protected abstract fun getSkippedByModelClass(lookup: Lookup, expectedText: String): String

  protected abstract fun getThresholds(): List<BaseThreshold>

  protected abstract fun getThresholdClass(value: Double?): String

  interface BaseThreshold {
    val value: Double
    val className: String

    operator fun Double.compareTo(t: BaseThreshold) = compareTo(t.value)
    operator fun compareTo(d: Double) = value.compareTo(d)
  }

  companion object {
    const val perfectLineSign: String = "\uD83C\uDF89" // :tada emoji:
    private val offsetRegex = "\"offset\":([0-9]+),".toRegex()
  }
}
