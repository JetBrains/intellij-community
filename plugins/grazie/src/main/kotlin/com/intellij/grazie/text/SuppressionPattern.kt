package com.intellij.grazie.text

import com.intellij.grazie.GrazieConfig

class SuppressionPattern(errorText: CharSequence, sentenceText: String?) {
  val errorText : String = normalize(errorText)
  val sentenceText : String? = sentenceText?.let(::normalize)
  val full : String = this.errorText + (if (sentenceText == null) "" else "|" + this.sentenceText)

  private fun normalize(text: CharSequence) = text.replace(Regex("\\s+"), " ").trim()
  
  fun isSuppressed(): Boolean = full in GrazieConfig.get().suppressingContext.suppressed
}
