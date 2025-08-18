package com.intellij.grazie.utils

import ai.grazie.gec.model.problem.ProblemHighlighting
import ai.grazie.nlp.langs.Language
import com.intellij.grazie.detection.LangDetector
import com.intellij.openapi.util.TextRange
import ai.grazie.text.TextRange as GrazieTextRange

fun getLanguageIfAvailable(text: String): Language? {
  return LangDetector.getLanguage(text)?.takeIf { HighlightingUtil.findInstalledLang(it) != null }
}

fun GrazieTextRange.Companion.coveringIde(ranges: Array<GrazieTextRange>): TextRange? {
  if (ranges.isEmpty()) return null
  return TextRange(ranges.minOf { it.start }, ranges.maxOf { it.endExclusive })
}

val ProblemHighlighting.underline: TextRange?
  get() = GrazieTextRange.coveringIde(this.always)