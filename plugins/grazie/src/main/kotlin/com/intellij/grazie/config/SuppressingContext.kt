package com.intellij.grazie.config

import com.intellij.grazie.grammar.Typo
import com.intellij.util.xmlb.annotations.Property

data class SuppressingContext(@Property val suppressed: Set<String> = HashSet()) {
  companion object {
    private val whitespace = Regex("\\s+")

    /** Preview in UI literal that will be suppressed */
    fun preview(typo: Typo) = normalize(typo) ?: ""

    private fun normalize(typo: Typo) = typo.location.patternText?.replace(whitespace, " ")?.trim()
  }

  fun suppress(typo: Typo): SuppressingContext {
    val code = normalize(typo) ?: return this
    return SuppressingContext(suppressed + code)
  }

  fun unsuppress(typo: Typo): SuppressingContext {
    val code = normalize(typo) ?: return this
    return SuppressingContext(suppressed - code)
  }

  fun isSuppressed(typo: Typo) = normalize(typo) in suppressed
}