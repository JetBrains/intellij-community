// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_RESPONSE
import com.intellij.cce.evaluable.AIA_USER_PROMPT
import com.intellij.cce.evaluable.REFERENCE_PROPERTY
import com.intellij.cce.workspace.info.FileEvaluationInfo
import com.intellij.cce.workspace.storages.FeaturesStorage
import kotlinx.html.*
import kotlinx.html.stream.createHTML

class ChatQuestionAnsweringReportGenerator(
  filterName: String,
  comparisonFilterName: String,
  featuresStorages: List<FeaturesStorage>,
  dirs: GeneratorDirectories
) : FileReportGenerator(featuresStorages, dirs, filterName, comparisonFilterName) {

  override fun getHtml(fileEvaluations: List<FileEvaluationInfo>, resourcePath: String, text: String): String {
    val sessions = fileEvaluations.map { it.sessionsInfo.sessions }
    return createHTML().body {
      textBlocks(sessions)
      for (resource in scripts) {
        script { src = resource.destinationPath }
      }
    }
  }
  override val scripts: List<Resource> = listOf()

  private fun BODY.textBlocks(sessions: List<List<Session>>) {
    var sessionInfoIndex = 0

    for (sessionList in sessions) {
      for (session in sessionList) {
        val firstLookup = session.lookups[0]

        val prompt = firstLookup.additionalInfo[AIA_USER_PROMPT]?.toString() ?: "No Prompt Available"
        val response = firstLookup.additionalInfo[AIA_RESPONSE]?.toString() ?: "No Response Available"
        val reference = firstLookup.additionalInfo[REFERENCE_PROPERTY]?.toString() ?: "No Reference Available"

        renderSessionInfo(sessionInfoIndex, prompt, reference, response)

        sessionInfoIndex++
      }
    }
  }

  private fun BODY.renderSessionInfo(index: Int, prompt: String, reference: String, response: String) {
    div {
      style = blockStyle()

      renderSection(this, "Prompt $index", prompt)
      renderSection(this, "Reference $index", reference)
      renderSection(this, "Response $index", response)
    }
  }

  private fun renderSection(container: DIV, title: String, content: String) {
    container.h3 {
      style = "font-size: 1em; font-weight: bold;"
      +title
    }
    container.pre("code") {
      style = preStyle()
      unsafe { raw(content) }
    }
  }

  private fun blockStyle(): String {
    return """
            border: 1px solid #ccc;
            padding: 16px;
            margin-bottom: 16px;
            border-radius: 8px;
            max-width: 100%;
            overflow: hidden;
            word-wrap: break-word;
            background-color: #f9f9f9;
        """.trimIndent()
  }

  private fun preStyle(): String {
    return """
            overflow-x: auto;
            max-width: 100%;
            white-space: pre-wrap;
            word-wrap: break-word;
        """.trimIndent()
  }
}
