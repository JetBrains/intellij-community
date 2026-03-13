package com.intellij.grazie.text

import ai.grazie.gec.model.problem.Category
import ai.grazie.gec.model.problem.Problem
import ai.grazie.gec.model.problem.ProblemFix
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.grazie.utils.ijRange
import com.intellij.openapi.util.TextRange
import com.intellij.util.text.StringOperation

open class GrazieProblem(val source: Problem, rule: Rule, text: TextContent):
  TextProblem(rule, text, source.highlighting.always.map { TextRange(it.start, it.endExclusive) }) {
  override fun getShortMessage(): String = source.message
  override fun getDescriptionTemplate(isOnTheFly: Boolean): @InspectionMessage String = shortMessage
  override fun isSpellingProblem(): Boolean = source.info.category == Category.SPELLING
  open fun copyWithProblemFixes(fixes: List<ProblemFix>): GrazieProblem = GrazieProblem(copyWithFixes(source, fixes), rule, text)
  open fun copyWithHighlighting(always: Array<ai.grazie.text.TextRange>, onHover: Array<ai.grazie.text.TextRange>): GrazieProblem =
    GrazieProblem(copyWithHighlighting(source, always, onHover), rule, text)
  open fun copyWithInfoAndMessage(info: Problem.KindInfo, message: String): GrazieProblem = GrazieProblem(copyWithInfoAndMessage(source, info, message), rule, text)

  override fun getSuggestions(): List<Suggestion> {
    return source.fixes.map { fix ->
      object : Suggestion {
        override fun getChanges(): List<StringOperation> =
          fix.changes.map { StringOperation.replace(it.ijRange(), it.text) }

        override fun getPresentableText(): String = getQuickFixText(fix)
        override fun getBatchId(): String? = fix.batchId
      }
    }
  }

  companion object {
    @JvmStatic
    fun visualizeSpace(s: String): String {
      return s.replace("\n", "⏎")
    }

    @JvmStatic
    fun getQuickFixText(fix: ProblemFix): String {
      return fix.customDisplayName ?: visualizeSpace(fix.parts.joinToString(separator = "") { it.display })
    }

    @JvmStatic
    fun copyWithFixes(problem: Problem, fixes: List<ProblemFix>): Problem = problem.copy(fixes = fixes.toTypedArray())

    @JvmStatic
    fun copyWithHighlighting(problem: Problem, always: Array<ai.grazie.text.TextRange>, onHover: Array<ai.grazie.text.TextRange>): Problem =
      problem.copy(highlighting = problem.highlighting.copy(always = always, onHover = onHover))

    @JvmStatic
    fun copyWithInfoAndMessage(problem: Problem, info: Problem.KindInfo, message: String): Problem = problem.copy(info = info, message = message)
  }
}

