package com.intellij.grazie.text

import com.intellij.grazie.GrazieConfig

class SuppressionPattern(errorText: CharSequence, sentenceText: String?) {
  internal val errorText : String = normalize(errorText)
  internal val sentenceText : String? = sentenceText?.let(::normalize)
  internal val full : String = this.errorText + (if (sentenceText == null) "" else "|" + this.sentenceText)

  private fun normalize(text: CharSequence) = text.replace(Regex("\\s+"), " ").trim()
  
  fun isSuppressed(): Boolean = full in GrazieConfig.get().suppressingContext.suppressed
}
