package com.intellij.grazie.grammar

import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.detection.LangDetector
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.text.*
import com.intellij.grazie.utils.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.ClassLoaderUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.util.ExceptionUtil
import com.intellij.util.containers.Interner
import kotlinx.html.*
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.VisibleForTesting
import org.languagetool.JLanguageTool
import org.languagetool.Languages
import org.languagetool.markup.AnnotatedTextBuilder
import org.languagetool.rules.RuleMatch
import org.slf4j.LoggerFactory
import java.util.*

@VisibleForTesting
class LanguageToolChecker : TextChecker() {
  override fun getRules(locale: Locale): Collection<Rule> {
    val language = Languages.getLanguageForLocale(locale)
    val state = GrazieConfig.get();
    val lang = state.enabledLanguages.find { language == it.jLanguage } ?: return emptyList()
    return getRules(lang, state)
  }

  override fun check(extracted: TextContent): @NotNull List<TextProblem> {
    val warnings = checkText(extracted)
    return warnings.filterNot { extracted.hasUnknownFragmentsIn(it.patternRange) }
  }

  class Problem(private val match: RuleMatch, lang: Lang, text: TextContent)
    : TextProblem(LanguageToolRule(lang, match.rule), text, TextRange(match.fromPos, match.toPos)) {

    override fun getShortMessage(): String =
      match.shortMessage.trimToNull() ?: match.rule.description.trimToNull() ?: match.rule.category.name

    override fun getDescriptionTemplate(isOnTheFly: Boolean) = toDescriptionTemplate(match, isOnTheFly)
    override fun getReplacementRange() = highlightRange
    override fun getCorrections(): List<String> = match.suggestedReplacements
    override fun getPatternRange() = TextRange(match.patternFromPos, match.patternToPos)

    override fun fitsGroup(group: RuleGroup): Boolean {
      return super.fitsGroup(group) || group.rules.any { id -> isAbstractCategory(id) && match.rule.id == id }
    }

    private fun isAbstractCategory(id: String) =
      id == RuleGroup.SENTENCE_END_PUNCTUATION || id == RuleGroup.SENTENCE_START_CASE || id == RuleGroup.UNLIKELY_OPENING_PUNCTUATION
  }

  companion object {
    private val logger = LoggerFactory.getLogger(LanguageToolChecker::class.java)
    private val interner = Interner.createWeakInterner<String>()

    internal fun getRules(lang: Lang, state: GrazieConfig.State = GrazieConfig.get()): List<LanguageToolRule> {
      return LangTool.getTool(lang, state).allRules.asSequence()
        .distinctBy { it.id }
        .filter { r -> !r.isDictionaryBasedSpellingRule }
        .map { LanguageToolRule(lang, it) }
        .toList()
    }

    @VisibleForTesting
    fun checkText(text: TextContent): List<Problem> {
      val str = text.toString()
      if (str.isBlank()) return emptyList()

      val lang = LangDetector.getLang(str) ?: return emptyList()

      return try {
        ClassLoaderUtil.computeWithClassLoader<List<Problem>, Throwable>(GraziePlugin.classLoader) {
          val annotated = AnnotatedTextBuilder().addText(str).build()
          LangTool.getTool(lang).check(annotated, true, JLanguageTool.ParagraphHandling.NORMAL,
                                       null, JLanguageTool.Mode.ALL, JLanguageTool.Level.PICKY)
            .asSequence()
            .filterNotNull()
            .map { Problem(it, lang, text) }
            .toList()
        }
      }
      catch (e: Throwable) {
        if (ExceptionUtil.causedBy(e, ProcessCanceledException::class.java)) {
          throw ProcessCanceledException()
        }

        logger.warn("Got exception during check for typos by LanguageTool", e)
        emptyList()
      }
    }

    @NlsSafe
    private fun toDescriptionTemplate(match: RuleMatch, isOnTheFly: Boolean): String {
      if (ApplicationManager.getApplication().isUnitTestMode) return match.rule.id
      val html = html {
        val withCorrections = match.rule.incorrectExamples.filter { it.corrections.isNotEmpty() }.takeIf { it.isNotEmpty() }
        val incorrectExample = (withCorrections ?: match.rule.incorrectExamples).minByOrNull { it.example.length }
        p {
          incorrectExample?.let {
            style = "padding-bottom: 8px;"
          }

          +match.messageSanitized
          if (!isOnTheFly) nbsp()
        }

        table {
          cellpading = "0"
          cellspacing = "0"

          incorrectExample?.let {
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
