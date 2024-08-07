// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.core.Session
import com.intellij.cce.workspace.info.FileEvaluationInfo
import com.intellij.cce.workspace.storages.FeaturesStorage
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.apache.commons.lang3.StringEscapeUtils

open class BasicFileReportGenerator(
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
      for (resource in scripts){
        script { src = resource.destinationPath }
      }
    }
  }

  protected open fun textToInsert(session: Session): String = session.expectedText

  override val scripts: List<Resource> = listOf(Resource("/script.js", "../res/script.js"))

  private fun BODY.codeBlocks(text: String, sessions: List<List<Session>>, maxLookupOrder: Int) {
    div {
      for (lookupOrder in 0..maxLookupOrder) {
        div("code-container ${if (lookupOrder != maxLookupOrder) "order-hidden" else ""}") {
          codeContainer(this, text, sessions, lookupOrder)
        }
      }
    }
  }

  protected open fun codeContainer(containerDiv: DIV, text: String, sessions: List<List<Session>>, lookupOrder: Int) = containerDiv.apply {
    div { pre("line-numbers") { +getLineNumbers(text.lines().size) } }
    div { pre("code") { unsafe { raw(prepareCode(text, sessions, lookupOrder)) } } }
  }

  private fun getLineNumbers(linesCount: Int): String =
    (1..linesCount).joinToString("\n") { it.toString().padStart(linesCount.toString().length) }

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
        val commonText = StringEscapeUtils.escapeHtml4(text.substring(offset, session.offset))
        append(commonText)

        val textToInsert = textToInsert(session)
        val center = textToInsert.length / sessions.size
        var shift = 0
        for (j in 0 until sessionGroup.lastIndex) {
          val subToken = if (center == 0) textToInsert else textToInsert.substring(shift, shift + center)
          append(getSpan(sessionGroup[j], subToken, lookupOrder))
          append(delimiter)
          shift += center
        }
        append(getSpan(sessionGroup.last(), textToInsert.substring(shift), lookupOrder))
        offset = session.offset + textToInsert.length
      }
      append(StringEscapeUtils.escapeHtml4(text.substring(offset)))
      toString()
    }
  }

  protected open fun getSpan(session: Session?, text: String, lookupOrder: Int): String =
    createHTML().span("session ${
      ReportColors.getColor(
        session,
        HtmlColorClasses,
        lookupOrder
      )
    }") {
      id = "${session?.id} $lookupOrder"
      +text
    }
}
