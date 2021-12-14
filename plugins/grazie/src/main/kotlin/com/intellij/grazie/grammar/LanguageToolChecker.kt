package com.intellij.grazie.grammar

import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.detection.LangDetector
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.text.*
import com.intellij.grazie.utils.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.ClassLoaderUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.util.ExceptionUtil
import com.intellij.util.containers.Interner
import kotlinx.html.*
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.VisibleForTesting
import org.languagetool.JLanguageTool
import org.languagetool.Languages
import org.languagetool.markup.AnnotatedTextBuilder
import org.languagetool.rules.GenericUnpairedBracketsRule
import org.languagetool.rules.RuleMatch
import org.slf4j.LoggerFactory
import java.util.*

@VisibleForTesting
open class LanguageToolChecker : TextChecker() {
  override fun getRules(locale: Locale): Collection<Rule> {
    val language = Languages.getLanguageForLocale(locale)
    val state = GrazieConfig.get()
    val lang = state.enabledLanguages.find { language == it.jLanguage } ?: return emptyList()
    return grammarRules(LangTool.getTool(lang), lang)
  }

  override fun check(extracted: TextContent): @NotNull List<TextProblem> {
    val str = extracted.toString()
    if (str.isBlank()) return emptyList()

    val lang = LangDetector.getLang(str) ?: return emptyList()

    return try {
      ClassLoaderUtil.computeWithClassLoader<List<TextProblem>, Throwable>(GraziePlugin.classLoader) {
        val tool = LangTool.getTool(lang)
        val sentences = tool.sentenceTokenize(str)
        if (sentences.any { it.length > 1000 }) emptyList()
        else {
          val annotated = AnnotatedTextBuilder().addText(str).build()
          val matches = tool.check(annotated, true, JLanguageTool.ParagraphHandling.NORMAL,
            null, JLanguageTool.Mode.ALL, JLanguageTool.Level.PICKY)
          matches.asSequence()
            .map { Problem(it, lang, extracted, this is TestChecker) }
            .filterNot { isGitCherryPickedFrom(it.match, extracted) }
            .filterNot { isKnownLTBug(it.match, extracted) }
            .filterNot { extracted.hasUnknownFragmentsIn(it.patternRange) }
            .toList()
        }
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

  private class Problem(val match: RuleMatch, lang: Lang, text: TextContent, val testDescription: Boolean)
    : TextProblem(LanguageToolRule(lang, match.rule), text, TextRange(match.fromPos, match.toPos)) {

    override fun getShortMessage(): String =
      match.shortMessage.trimToNull() ?: match.rule.description.trimToNull() ?: match.rule.category.name

    override fun getDescriptionTemplate(isOnTheFly: Boolean): String =
      if (testDescription) match.rule.id
      else match.messageSanitized

    override fun getTooltipTemplate(): String = toTooltipTemplate(match)

    override fun getReplacementRange(): TextRange = highlightRanges[0]
    override fun getCorrections(): List<String> = match.suggestedReplacements
    override fun getPatternRange() = TextRange(match.patternFromPos, match.patternToPos)

    override fun fitsGroup(group: RuleGroup): Boolean {
      val highlightRange = highlightRanges[0]
      val ruleId = match.rule.id
      if (RuleGroup.INCOMPLETE_SENTENCE in group.rules) {
        if (highlightRange.startOffset == 0 &&
            (ruleId == "SENTENCE_FRAGMENT" || ruleId == "SENT_START_CONJUNCTIVE_LINKING_ADVERB_COMMA" || ruleId == "AGREEMENT_SENT_START")) {
          return true
        }
        if (ruleId == "MASS_AGREEMENT" && text.subSequence(highlightRange.endOffset, text.length).startsWith(".")) {
          return true
        }
      }

      if (RuleGroup.UNDECORATED_SENTENCE_SEPARATION in group.rules && ruleId in sentenceSeparationRules) {
        return true
      }

      return super.fitsGroup(group) || group.rules.any { id -> isAbstractCategory(id) && ruleId == id }
    }

    private fun isAbstractCategory(id: String) =
      id == RuleGroup.SENTENCE_END_PUNCTUATION || id == RuleGroup.SENTENCE_START_CASE || id == RuleGroup.UNLIKELY_OPENING_PUNCTUATION
  }

  companion object {
    private val logger = LoggerFactory.getLogger(LanguageToolChecker::class.java)
    private val interner = Interner.createWeakInterner<String>()
    private val sentenceSeparationRules = setOf("LC_AFTER_PERIOD", "PUNT_GEEN_HL", "KLEIN_NACH_PUNKT")

    internal fun grammarRules(tool: JLanguageTool, lang: Lang): List<LanguageToolRule> {
      return tool.allRules.asSequence()
        .distinctBy { it.id }
        .filter { r -> !r.isDictionaryBasedSpellingRule }
        .map { LanguageToolRule(lang, it) }
        .toList()
    }

    /**
     * Git adds "cherry picked from", which doesn't seem entirely grammatical,
     * but zillions of tools depend on this message, and it's unlikely to be changed.
     * So we ignore this pattern in commit messages and literals (which might be used for parsing git output)
     */
    private fun isGitCherryPickedFrom(match: RuleMatch, text: TextContent): Boolean {
      return match.rule.id == "EN_COMPOUNDS" && match.fromPos > 0 && text.startsWith("(cherry picked from", match.fromPos - 1) &&
             (text.domain == TextContent.TextDomain.LITERALS ||
              text.domain == TextContent.TextDomain.PLAIN_TEXT && CommitMessage.isCommitMessage(text.containingFile))
    }

    private fun isKnownLTBug(match: RuleMatch, text: TextContent): Boolean {
      if (match.rule is GenericUnpairedBracketsRule && match.fromPos > 0 &&
          (text.startsWith("\")", match.fromPos - 1) || text.subSequence(0, match.fromPos).contains("(\""))) {
        return true //https://github.com/languagetool-org/languagetool/issues/5269
      }

      if (match.rule.id == "ARTICLE_ADJECTIVE_OF" && text.substring(match.fromPos, match.toPos).equals("iterable", ignoreCase = true)) {
        return true // https://github.com/languagetool-org/languagetool/issues/5270
      }

      if (match.rule.id == "THIS_NNS_VB" && text.subSequence(match.toPos, text.length).matches(Regex("\\s+reverts\\s.*"))) {
        return true // https://github.com/languagetool-org/languagetool/issues/5455
      }

      if (match.rule.id.endsWith("DOUBLE_PUNCTUATION") &&
          (isNumberRange(match.fromPos, match.toPos, text) || isPathPart(match.fromPos, match.toPos, text))) {
        return true
      }

      return false
    }

    // https://github.com/languagetool-org/languagetool/issues/5230
    private fun isNumberRange(startOffset: Int, endOffset: Int, text: TextContent): Boolean {
      return startOffset > 0 && endOffset < text.length && text[startOffset - 1].isDigit() && text[endOffset].isDigit()
    }

    // https://github.com/languagetool-org/languagetool/issues/5883
    private fun isPathPart(startOffset: Int, endOffset: Int, text: TextContent): Boolean {
      return text.subSequence(0, startOffset).endsWith('/') || text.subSequence(endOffset, text.length).startsWith('/')
    }

    @NlsSafe
    private fun toTooltipTemplate(match: RuleMatch): String {
      val html = html {
        val withCorrections = match.rule.incorrectExamples.filter { it.corrections.isNotEmpty() }.takeIf { it.isNotEmpty() }
        val incorrectExample = (withCorrections ?: match.rule.incorrectExamples).minByOrNull { it.example.length }
        p {
          incorrectExample?.let {
            style = "padding-bottom: 8px;"
          }

          +match.messageSanitized
          nbsp()
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
                nbsp()
              }
              td {
                style = "width: 100%;"
                toIncorrectHtml(it)
                nbsp()
              }
            }

            if (it.corrections.isNotEmpty()) {
              tr {
                td {
                  valign = "top"
                  style = "padding-top: 5px; padding-right: 5px; color: gray; vertical-align: top;"
                  +" "
                  +GrazieBundle.message("grazie.settings.grammar.rule.correct")
                  +" "
                  nbsp()
                }
                td {
                  style = "padding-top: 5px; width: 100%;"
                  toCorrectHtml(it)
                  nbsp()
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

  class TestChecker: LanguageToolChecker()

}
