package com.intellij.grazie.jlanguage.filters

import org.languagetool.markup.AnnotatedText
import org.languagetool.rules.RuleMatch
import org.languagetool.rules.RuleMatchFilter
import org.languagetool.rules.SuggestedReplacement

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
    return buildList {
      for (match in ruleMatches) {
        val error = text.plainText.subSequence(match.fromPos, match.toPos)
        if (isUpperCase(error)) {
          val replacements = match.suggestedReplacements.map { SuggestedReplacement(it.uppercase()) }
          add(RuleMatch(match, replacements))
        } else {
          add(match)
        }
      }
    }
  }
}
