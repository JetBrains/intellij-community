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
import java.net.URL

internal class LanguageToolRule(
  private val lang: Lang, private val ltRule: org.languagetool.rules.Rule
) : Rule(LangTool.globalIdPrefix(lang) + ltRule.id, ltRule.description, ltRule.category.name) {

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
}