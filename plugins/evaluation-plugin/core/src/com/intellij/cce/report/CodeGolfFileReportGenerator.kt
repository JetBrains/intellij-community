package com.intellij.cce.report

import com.intellij.cce.actions.selectedWithoutPrefix
import com.intellij.cce.core.Lookup
import com.intellij.cce.core.Session
import com.intellij.cce.core.SuggestionKind
import com.intellij.cce.workspace.info.FileEvaluationInfo
import com.intellij.cce.workspace.storages.FeaturesStorage
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.apache.commons.lang.StringEscapeUtils

class CodeGolfFileReportGenerator(
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
          delOption("delimiter", "&int;")
          delOption("del-box-small", "&#10073;")
          delOption("del-box-big", "&#10074;")
          delOption("del-none", "empty")
        }
      }
      code {
        table {
          fileEvaluations.forEach {
            getTable(it.sessionsInfo.sessions, text, it.evaluationType)
          }
        }
      }
      script { src = "../res/script.js" }
      script { +"isCodeGolf = true" }
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

  private fun TABLE.getTable(sessions: List<Session>, fullText: String, evaluationId: String) {
    tbody("evalContent") {
      id = evaluationId
      var offset = 0
      var lineNumbers = 0
      sessions.forEach { session ->
        val text = fullText.substring(offset, session.offset)
        val tab = text.split("\n").last().dropWhile { it == '\n' }
        val skipped = text.split("\n").dropLast(1)

        skipped.takeIf { it.isNotEmpty() }?.forEach {
          if (it.isNotEmpty()) {
            tr {
              td("line-numbers") {
                attributes["data-line-numbers"] = lineNumbers.toString()
              }
              td("code-line") {
                span { +it }
              }
              lineNumbers++
            }
          }
        }

        tr {
          td("line-numbers") {
            attributes["data-line-numbers"] = lineNumbers.toString()
          }
          td("code-line") {
            prepareLine(session.expectedText, session.lookups, tab, session.id)
          }
        }
        offset = session.offset + session.expectedText.length
        lineNumbers++
      }
    }
  }

  private fun FlowContent.prepareLine(expectedText: String, lookups: List<Lookup>, tab: String, id: String) {
    consumer.onTagContentUnsafe { +StringEscapeUtils.escapeHtml(tab) }
    var offset = 0

    lookups.dropLast(1).forEachIndexed { index, lookup ->
      offset = prepareSpan(expectedText, lookup, id, index, offset, "delimiter")
    }
    prepareSpan(expectedText, lookups.last(), id, lookups.size - 1, offset)
  }

  private fun FlowContent.prepareSpan(expectedText: String,
                                      lookup: Lookup,
                                      uuid: String,
                                      columnId: Int,
                                      offset: Int,
                                      delimiter: String = ""): Int {
    val kinds = lookup.suggestions.map { suggestion -> suggestion.kind }
    val kindClass = when {
      SuggestionKind.LINE in kinds -> "cg-line"
      SuggestionKind.TOKEN in kinds -> "cg-token"
      else -> "cg-none"
    }

    val text = lookup.selectedWithoutPrefix() ?: expectedText[offset].toString()

    span("completion $kindClass $delimiter") {
      id = uuid
      attributes["data-cl"] = "$columnId"
      attributes["data-id"] = uuid
      +text
    }
    return offset + text.length
  }
}
