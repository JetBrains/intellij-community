package com.intellij.grazie.grammar.suppress

import com.intellij.grazie.grammar.Typo
import com.intellij.util.xmlb.annotations.Property

data class SuppressionContext(@Property val rules: Map<String, Set<Int>> = HashMap()) {
  companion object {
    private val whitespace = Regex("\\s+")

    private fun calculateCode(typo: Typo) = typo.location.patternText?.replace(whitespace, " ")?.hashCode()
  }

  fun suppress(typo: Typo): SuppressionContext {
    val map = rules.toMutableMap()
    val code = calculateCode(typo) ?: return this
    map[typo.id] = rules[typo.id].orEmpty() + code
    return SuppressionContext(map)
  }

  fun unsuppress(typo: Typo): SuppressionContext {
    val map = rules.toMutableMap()
    val code = calculateCode(typo) ?: return this

    val newCodes = rules[typo.id].orEmpty() - code
    if (newCodes.isNotEmpty()) {
      map[typo.id] = newCodes
    }
    else {
      map.remove(typo.id)
    }

    return SuppressionContext(map)
  }

  fun isSuppressed(typo: Typo) = calculateCode(typo) in rules[typo.id].orEmpty()

  private val Typo.id: String
    get() = info.rule.id
}