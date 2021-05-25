package com.intellij.grazie.grammar

import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grammar.GrammarEngine.getTypos
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.text.Rule
import com.intellij.grazie.text.TextChecker
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextProblem
import com.intellij.grazie.utils.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.util.containers.Interner
import kotlinx.html.*
import org.jetbrains.annotations.NotNull
import org.languagetool.Languages
import java.util.*

internal class LanguageToolChecker : TextChecker() {
  override fun getRules(locale: Locale): Collection<Rule?> {
    val language = Languages.getLanguageForLocale(locale)
    val lang = GrazieConfig.get().enabledLanguages.find { language == it.jLanguage } ?: return emptyList()
    val activeIds = defaultEnabledIds(lang)
    return defaultTool(lang).allRules.asSequence()
      .distinctBy { it.id }
      .filter { r -> !r.isDictionaryBasedSpellingRule }
      .map { toGrazieRule(it, activeIds) }
      .toList()
  }

  private fun defaultEnabledIds(lang: Lang) = defaultTool(lang).allActiveRules.map { it.id }.toSet()

  private fun defaultTool(lang: Lang) = LangTool.getTool(lang, GrazieConfig.State())

  private fun toGrazieRule(rule: org.languagetool.rules.Rule, activeIds: Set<String>) =
    LanguageToolRule(rule, activeIds.contains(rule.id))

  override fun check(extracted: TextContent): @NotNull List<TextProblem> {
    val str = extracted.toString()
    val warnings = getTypos(str, 0)
    return warnings.mapNotNull { typo ->
      val location = typo.location
      val patternRange = location.patternRange.toTextRange()
      if (!extracted.hasUnknownFragmentsIn(patternRange)) {
        val rule = toGrazieRule(typo.info.rule, defaultEnabledIds(typo.info.lang))
        val range = location.errorRange.toTextRange()
        object : TextProblem(rule, extracted, range) {
          override fun getShortMessage() = typo.info.shortMessage
          override fun getDescriptionTemplate(isOnTheFly: Boolean) = typo.toDescriptionTemplate(isOnTheFly)
          override fun getReplacementRange() = range
          override fun getCorrections() = typo.fixes.toList()
          override fun getPatternRange() = patternRange
        }
      }
      else null
    }
  }

  private fun IntRange.toTextRange() = TextRange.create(start, endInclusive + 1)

  @Suppress("UnstableApiUsage")
  companion object {
    private val interner = Interner.createWeakInterner<String>()

    @NlsSafe
    private fun Typo.toDescriptionTemplate(isOnTheFly: Boolean): String {
      if (ApplicationManager.getApplication().isUnitTestMode) return info.rule.id
      val html = html {
        p {
          info.incorrectExample?.let {
            style = "padding-bottom: 8px;"
          }

          +info.message
          if (!isOnTheFly) nbsp()
        }

        table {
          cellpading = "0"
          cellspacing = "0"

          info.incorrectExample?.let {
            tr {
              td {
                valign = "top"
                style = "padding-right: 5px; color: gray; vertical-align: top;"
                +" "
                +GrazieBundle.message("grazie.settings.grammar.rule.incorrect")
                +" "
                if (!isOnTheFly) nbsp()
              }
              td {
                style = "width: 100%;"
                toIncorrectHtml(it)
                if (!isOnTheFly) nbsp()
              }
            }

            if (it.corrections.any { correction -> correction.isNullOrBlank().not() }) {
              tr {
                td {
                  valign = "top"
                  style = "padding-top: 5px; padding-right: 5px; color: gray; vertical-align: top;"
                  +" "
                  +GrazieBundle.message("grazie.settings.grammar.rule.correct")
                  +" "
                  if (!isOnTheFly) nbsp()
                }
                td {
                  style = "padding-top: 5px; width: 100%;"
                  toCorrectHtml(it)
                  if (!isOnTheFly) nbsp()
                }
              }
            }
          }
        }

        p {
          style = "text-align: left; font-size: x-small; color: gray; padding-top: 10px; padding-bottom: 0px;"
          +" "
          +GrazieBundle.message("grazie.tooltip.powered-by-language-tool")
        }
      }
      return interner.intern(html)
    }
  }

}