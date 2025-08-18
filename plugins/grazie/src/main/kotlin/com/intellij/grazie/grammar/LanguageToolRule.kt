package com.intellij.grazie.grammar

import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.ide.ui.components.utils.html
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.text.Rule
import kotlinx.html.*
import org.languagetool.JLanguageTool
import org.languagetool.rules.Categories
import org.languagetool.rules.ITSIssueType
import org.languagetool.rules.IncorrectExample
import java.net.URL
import java.util.*

// ltRule used in ReSharper
class LanguageToolRule(
  private val lang: Lang, val ltRule: org.languagetool.rules.Rule, private val similarLtRules: List<org.languagetool.rules.Rule> = emptyList(),
) : Rule(LangTool.globalIdPrefix(lang) + ltRule.id, ltRule.description, categories(ltRule, lang)) {

  override fun isEnabledByDefault(): Boolean = LangTool.isRuleEnabledByDefault(lang, ltRule.id)

  override fun getUrl(): URL? = similarLtRules.map { it.url }.toSet().singleOrNull()

  override fun getDescription(): String = html {
    table {
      cellpading = "0"
      cellspacing = "0"
      style = "width:100%;"

      ltRule.incorrectExamples.forEach { example ->
        tr {
          td {
            valign = "top"
            style = "padding-bottom: 5px; padding-right: 5px; color: gray; white-space: nowrap;"
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
              style = "padding-bottom: 10px; padding-right: 5px; color: gray; white-space: nowrap;"
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

  companion object {
    private fun categoryName(kind: Categories, lang: Lang, orElse: String): String {
      try {
        return kind.getCategory(JLanguageTool.getMessageBundle(lang.jLanguage!!)).name
      }
      catch (_: MissingResourceException) {
        return orElse
      }
    }

    private fun categories(ltRule: org.languagetool.rules.Rule, lang: Lang): List<String> {
      val ltCat = ltRule.category
      if (isStyleLike(ltRule)) {
        val subCat = when (ltCat.id) {
          Categories.STYLE.id, Categories.MISC.id -> categoryName(Categories.MISC, lang, "Other")
          else -> ltCat.name
        }
        return listOf(categoryName(Categories.STYLE, lang, "Style"), subCat)
      }
      return listOf(ltCat.name)
    }

    @JvmStatic
    fun isStyleLike(ltRule: org.languagetool.rules.Rule): Boolean =
      ltRule.locQualityIssueType == ITSIssueType.Style ||
      ltRule.category.id == Categories.STYLE.id ||
      ltRule.category.id == Categories.TYPOGRAPHY.id
  }

  private var TABLE.cellpading: String
    get() = attributes["cellpadding"] ?: ""
    set(value) {
      attributes["cellpadding"] = value
    }

  private var TABLE.cellspacing: String
    get() = attributes["cellspacing"] ?: ""
    set(value) {
      attributes["cellspacing"] = value
    }

  private var TD.valign: String
    get() = attributes["valign"] ?: ""
    set(value) {
      attributes["valign"] = value
    }

  private fun FlowOrPhrasingContent.toHtml(example: IncorrectExample, mistakeHandler: FlowOrPhrasingContent.(String) -> Unit) {
    Regex("(.*?)<marker>(.*?)</marker>(.*)").findAll(example.example).forEach {
      val (prefix, mistake, suffix) = it.destructured

      +prefix
      mistakeHandler(mistake)
      +suffix
    }
  }

  fun FlowOrPhrasingContent.toIncorrectHtml(example: IncorrectExample) {
    toHtml(example) { mistake ->
      if (mistake.isNotEmpty()) {
        strong {
          +mistake
        }
      }
    }
  }

  fun FlowOrPhrasingContent.toCorrectHtml(example: IncorrectExample) {
    toHtml(example) {
      if (example.corrections.isNotEmpty()) {
        strong {
          +example.corrections.joinToString(separator = " / ")
        }
      }
    }
  }
}