package com.intellij.grazie.jlanguage.filters

import org.languagetool.markup.AnnotatedText
import org.languagetool.rules.RuleMatch
import org.languagetool.rules.RuleMatchFilter
import java.util.*
import java.util.function.Consumer

class UppercaseMatchFilter : RuleMatchFilter {
  companion object {
    private fun isUpperCase(str: CharSequence): Boolean {
      for (index in str.indices) {
        if (Character.isLetter(str[index]) && !Character.isUpperCase(str[index])) return false
      }

      return true
    }
  }

  override fun filter(ruleMatches: List<RuleMatch>, text: AnnotatedText): List<RuleMatch> {
    val newRuleMatches: MutableList<RuleMatch> = ArrayList()

    ruleMatches.forEach(Consumer { ruleMatch: RuleMatch ->
      val replacements = ruleMatch.suggestedReplacements
      val newReplacements: MutableList<String> = ArrayList()
      val error = text.plainText.subSequence(ruleMatch.fromPos, ruleMatch.toPos)

      if (isUpperCase(error)) {
        for (replacement in replacements) {
          newReplacements.add(replacement.toUpperCase())
        }

        newRuleMatches.add(RuleMatch(ruleMatch, newReplacements))
      } else {
        newRuleMatches.add(ruleMatch)
      }
    })

    return newRuleMatches
  }
}
