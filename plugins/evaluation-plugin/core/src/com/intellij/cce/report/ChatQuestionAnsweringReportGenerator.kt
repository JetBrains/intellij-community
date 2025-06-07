// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.*
import com.intellij.cce.metric.LLMJudgeScore
import com.intellij.cce.workspace.info.FileEvaluationInfo
import com.intellij.cce.workspace.info.SessionIndividualScore
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
    return createHTML().body {
      for (fileEval in fileEvaluations) {
        val sessions = fileEval.sessionsInfo.sessions
        val sessionIndividualEvaluations = fileEval.metrics
          .flatMap { it.individualScores?.entries ?: emptySet() }
          .associate { it.key to it.value }
        textBlocks(sessions, sessionIndividualEvaluations)
      }
    }
  }
  override val scripts: List<Resource> = listOf()

  private fun BODY.textBlocks(
    sessions: List<Session>,
    sessionIndividualEvaluations: Map<String, SessionIndividualScore>
  ) {
    sessions.forEachIndexed { index, session ->
      val firstLookup = session.lookups.firstOrNull() ?: run {
        println("Session $index skipped due to missing lookup.")
        return@forEachIndexed
      }
      val prompt = firstLookup.additionalInfo[AIA_USER_PROMPT]?.toString() ?: "No Prompt Available"
      val response = firstLookup.additionalInfo[AIA_RESPONSE]?.toString() ?: "No Response Available"
      val reference = firstLookup.additionalInfo[REFERENCE_PROPERTY]?.toString() ?: "No Reference Available"
      val llmJudgeResponse = sessionIndividualEvaluations[session.id]?.additionalInfo?.get(LLM_JUDGE_RESPONSE)?.firstOrNull()?.toString() ?: "No LLM-as-a-Judge response is available"
      val llmJudgeScore = sessionIndividualEvaluations[session.id]?.metricScores?.get(LLMJudgeScore.NAME)?.firstOrNull()?.toString() ?: "No LLM-as-a-Judge score is available"

      renderSessionInfo(index, prompt, reference, response, llmJudgeResponse, llmJudgeScore)
    }
  }

  private fun BODY.renderSessionInfo(index: Int, prompt: String, reference: String, response: String, llmJudgeResponse: String, llmJudgeScore: String) {
    div {
      style = blockStyle()

      renderSection(this, "Prompt $index", prompt)
      renderSection(this, "Reference $index", reference)
      renderSection(this, "Response $index", response)
      renderSection(this, "LLM-as-a-Judge Raw Response:", llmJudgeResponse)

      renderInlineScore(this, "LLM-as-a-Judge Score:", llmJudgeScore)
    }
  }

  private fun renderInlineScore(container: DIV, title: String, score: String) {
    container.p {
      style = "font-size: 1em; font-weight: bold; margin-top: 16px; margin-bottom: 16px;"
      +"$title $score"
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
