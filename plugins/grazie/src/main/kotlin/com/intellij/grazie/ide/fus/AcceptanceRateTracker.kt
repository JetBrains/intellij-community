package com.intellij.grazie.ide.fus

import ai.grazie.nlp.langs.Language
import com.intellij.grazie.detection.LangDetector
import com.intellij.grazie.text.Rule
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextProblem
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.ui.CommitMessage
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
data class AcceptanceRateTracker(
  val rule: Rule,
  val domain: TextContent.TextDomain,
  val isCommitMessage: Boolean,
  val programmingLanguage: com.intellij.lang.Language,
  val textLanguage: Language,
  val filepath: String,
  val ranges: List<TextRange>,
) {
  constructor(problem: TextProblem) :
    this(
      problem.rule,
      problem.text.domain,
      CommitMessage.isCommitMessage(problem.text.commonParent),
      problem.text.commonParent.language,
      LangDetector.getLanguage(problem.text.toString()) ?: Language.UNKNOWN,
      problem.text.containingFile.viewProvider.virtualFile.path,
      problem.highlightRanges
    )

  private val shown: AtomicBoolean = AtomicBoolean(false)
  fun markShown(): Boolean {
    return shown.compareAndSet(false, true)
  }
}