package com.intellij.cce.report

import com.intellij.cce.core.Session
import com.intellij.cce.metric.SuggestionsComparator
import com.intellij.cce.workspace.info.FileEvaluationInfo
import com.intellij.cce.workspace.storages.FeaturesStorage
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.apache.commons.lang.StringEscapeUtils

class BasicFileReportGenerator(
  private val suggestionsComparators: List<SuggestionsComparator>,
  filterName: String,
  comparisonFilterName: String,
  featuresStorages: List<FeaturesStorage>,
  dirs: GeneratorDirectories
) : FileReportGenerator(featuresStorages, dirs, filterName, comparisonFilterName) {

  override fun getHtml(fileEvaluations: List<FileEvaluationInfo>, fileName: String, resourcePath: String, text: String): String {
    val sessions = fileEvaluations.map { it.sessionsInfo.sessions }
    val maxLookupOrder = sessions.flatMap { session -> session.map { it.lookups.size - 1 } }.maxOrNull() ?: 0
    return createHTML().body {
      if (maxLookupOrder != 0) label("label") {
        htmlFor = "lookup-order"
        +"Lookup order:"
      }
      if (maxLookupOrder != 0) input(InputType.number) {
        id = "lookup-order"
        min = 0.toString()
        max = maxLookupOrder.toString()
        value = maxLookupOrder.toString()
        onChange = "changeLookupOrder()"
      }
      codeBlocks(text, sessions, maxLookupOrder)
      script { src = "../res/script.js" }
    }
  }

  private fun getLineNumbers(linesCount: Int): String =
    (1..linesCount).joinToString("\n") { it.toString().padStart(linesCount.toString().length) }

  private fun BODY.codeBlocks(text: String, sessions: List<List<Session>>, maxLookupOrder: Int) {
    div {
      for (lookupOrder in 0..maxLookupOrder) {
        div("code-container ${if (lookupOrder != maxLookupOrder) "order-hidden" else ""}") {
          div { pre("line-numbers") { +getLineNumbers(text.lines().size) } }
          div { pre("code") { unsafe { raw(prepareCode(text, sessions, lookupOrder)) } } }
        }
      }
    }
  }

  private fun prepareCode(text: String, _sessions: List<List<Session>>, lookupOrder: Int): String {
    if (_sessions.isEmpty() || _sessions.all { it.isEmpty() }) return text

    val sessions = _sessions.filterNot { it.isEmpty() }
    return StringBuilder().run {
      val delimiter = "&int;"
      val offsets = sessions.flatten().map { it.offset }.distinct().sorted()
      val sessionGroups = offsets.map { offset -> sessions.map { session -> session.find { it.offset == offset } } }
      var offset = 0

      for (sessionGroup in sessionGroups) {
        val session = sessionGroup.filterNotNull().first()
        val commonText = StringEscapeUtils.escapeHtml(text.substring(offset, session.offset))
        append(commonText)

        val center = session.expectedText.length / sessions.size
        var shift = 0
        for (j in 0 until sessionGroup.lastIndex) {
          val subToken = if (center == 0) session.expectedText else session.expectedText.substring(shift, shift + center)
          append(getSpan(sessionGroup[j], subToken, lookupOrder, suggestionsComparators[j]))
          append(delimiter)
          shift += center
        }
        append(getSpan(sessionGroup.last(), session.expectedText.substring(shift), lookupOrder, suggestionsComparators.last()))
        offset = session.offset + session.expectedText.length
      }
      append(StringEscapeUtils.escapeHtml(text.substring(offset)))
      toString()
    }
  }

  private fun getSpan(session: Session?, text: String, lookupOrder: Int, suggestionsComparator: SuggestionsComparator): String =
    createHTML().span("completion ${
      ReportColors.getColor(
        session,
        HtmlColorClasses,
        lookupOrder,
        suggestionsComparator
      )
    }") {
      id = "${session?.id} $lookupOrder"
      +text
    }
}
