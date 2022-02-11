package com.intellij.grazie.grammar

import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.text.Rule
import com.intellij.grazie.utils.*
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr
import org.languagetool.JLanguageTool
import org.languagetool.rules.Categories
import org.languagetool.rules.ITSIssueType
import java.net.URL
import java.util.*

internal class LanguageToolRule(
  private val lang: Lang, private val ltRule: org.languagetool.rules.Rule
) : Rule(LangTool.globalIdPrefix(lang) + ltRule.id, ltRule.description,
         if (isStyleRule(ltRule)) categoryName(Categories.STYLE, lang, "Style") else ltRule.category.name
) {

  override fun getSubCategory(): String? {
    if (isStyleRule(ltRule)) {
      if (ltRule.category.id == Categories.STYLE.id || ltRule.category.id == Categories.MISC.id) {
        return categoryName(Categories.MISC, lang, "Other")
      }
      return ltRule.category.name
    }
    return null
  }

  override fun isEnabledByDefault(): Boolean = LangTool.isRuleEnabledByDefault(lang, ltRule.id)

  override fun getUrl(): URL? = ltRule.url

  override fun getDescription(): String = html {
    table {
      cellpading = "0"
      cellspacing = "0"
      style = "width:100%;"

      ltRule.incorrectExamples.forEach { example ->
        tr {
          td {
            valign = "top"
            style = "padding-bottom: 5px; padding-right: 5px; color: gray;"
            +GrazieBundle.message("grazie.settings.grammar.rule.incorrect")
          }
          td {
            style = "padding-bottom: 5px; width: 100%;"
            toIncorrectHtml(example)
          }
        }

        if (example.corrections.any { it.isNotBlank() }) {
          tr {
            td {
              valign = "top"
              style = "padding-bottom: 10px; padding-right: 5px; color: gray;"
              +GrazieBundle.message("grazie.settings.grammar.rule.correct")
            }
            td {
              style = "padding-bottom: 10px; width: 100%;"
              toCorrectHtml(example)
            }
          }
        }
      }
    }
    +GrazieBundle.message("grazie.tooltip.powered-by-language-tool")
  }

  override fun getSearchableDescription(): String = "LanguageTool"

  private companion object {
    private fun categoryName(kind: Categories, lang: Lang, orElse: String): String {
      try {
        return kind.getCategory(JLanguageTool.getMessageBundle(lang.jLanguage!!)).name
      }
      catch (e: MissingResourceException) {
        return orElse
      }
    }

    private fun isStyleRule(ltRule: org.languagetool.rules.Rule) =
      ltRule.locQualityIssueType == ITSIssueType.Style ||
      ltRule.category.id == Categories.STYLE.id ||
      ltRule.category.id == Categories.TYPOGRAPHY.id
  }
}