package com.intellij.grazie.grammar

import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.detection.LangDetector
import com.intellij.grazie.ide.ui.components.utils.html
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.text.*
import com.intellij.grazie.utils.trimToNull
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.ClassLoaderUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Predicates
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.util.ExceptionUtil
import com.intellij.util.containers.Interner
import kotlinx.html.p
import kotlinx.html.style
import org.jetbrains.annotations.ApiStatus
import org.languagetool.JLanguageTool
import org.languagetool.Languages
import org.languagetool.markup.AnnotatedTextBuilder
import org.languagetool.rules.GenericUnpairedBracketsRule
import org.languagetool.rules.RuleMatch
import org.languagetool.rules.en.EnglishUnpairedQuotesRule
import org.slf4j.LoggerFactory
import java.util.*
import java.util.function.Predicate


open class LanguageToolChecker : TextChecker() {
  @ApiStatus.Internal
  class TestChecker : LanguageToolChecker()

  override fun getRules(locale: Locale): Collection<Rule> {
    val language = Languages.getLanguageForLocale(locale)
    val lang = Lang.values().find { it.jLanguage == language } ?: return emptyList()
    return grammarRules(LangTool.getTool(lang), lang)
  }

  override fun check(extracted: TextContent): List<Problem> {
    val text = extracted.toString()
    if (text.isBlank()) {
      return emptyList()
    }

    val language = LangDetector.getLang(text) ?: return emptyList()
    try {
      return runBlockingCancellable {
        // LT will use indicator for cancelled checks
        coroutineToIndicator {
          val indicator = ProgressManager.getGlobalProgressIndicator()
          checkNotNull(indicator) { "Indicator was not set for current job" }
          return@coroutineToIndicator ApplicationUtil.runWithCheckCanceled(
            {
              ClassLoaderUtil.computeWithClassLoader<List<Problem>, Throwable>(GraziePlugin.classLoader) {
                collectLanguageToolProblems(extracted = extracted, text = text, lang = language)
              }
            },
            indicator
          )
        }
      }
    }
    catch (exception: Throwable) {
      if (ExceptionUtil.causedBy(exception, ProcessCanceledException::class.java)) {
        throw ProcessCanceledException()
      }
      logger.warn("Got exception from LanguageTool", exception)
    }
    return emptyList()
  }

  private fun collectLanguageToolProblems(extracted: TextContent, text: String, lang: Lang): List<Problem> {
    val tool = LangTool.getTool(lang)
    val sentences = tool.sentenceTokenize(text)
    if (sentences.any { it.length > 1000 }) {
      return emptyList()
    }
    val matches = runLT(tool, text)
    val disappearsAfterAddingQuotes by lazy { checkQuotedText(extracted, tool) }
    return matches.asSequence()
      .filterNot { possiblyMarkupDependent(it) && disappearsAfterAddingQuotes.test(it) }
      .map { Problem(it, lang, extracted, this is TestChecker) }
      .filterNot { isGitCherryPickedFrom(it.match, extracted) }
      .filterNot { isKnownLTBug(it.match, extracted) }
      .filterNot {
        val range = when {
          it.fitsGroup(RuleGroup.CASING) -> includeSentenceBounds(extracted, it.patternRange)
          else -> it.patternRange
        }
        extracted.hasUnknownFragmentsIn(range)
      }
      .toList()
  }

  private fun checkQuotedText(extracted: TextContent, tool: JLanguageTool): Predicate<RuleMatch> = when {
    extracted.markupOffsets().isEmpty() -> Predicates.alwaysFalse()
    else -> {
      val quotedText = extracted.replaceMarkupWith('"')
      val quotedMatches = runLT(tool, quotedText.toString())
      Predicate { om ->
        ProgressManager.checkCanceled()
        quotedMatches.none { qm ->
          qm.rule == om.rule &&
          quotedText.offsetToOriginal(qm.fromPos) == om.fromPos &&
          quotedText.offsetToOriginal(qm.toPos) == om.toPos
        }
      }
    }
  }

  private fun possiblyMarkupDependent(match: RuleMatch): Boolean {
    return match.message.lowercase().contains("capitalize") ||
           match.rule.id == "POSSESSIVE_APOSTROPHE"
  }

  private fun runLT(tool: JLanguageTool, str: String): List<RuleMatch> =
    tool.check(AnnotatedTextBuilder().addText(str).build(), true, JLanguageTool.ParagraphHandling.NORMAL,
               null, JLanguageTool.Mode.ALL, JLanguageTool.Level.PICKY)

  private fun includeSentenceBounds(text: CharSequence, range: TextRange): TextRange {
    var start = range.startOffset
    var end = range.endOffset

    while (start > 0 && text[start - 1].isWhitespace()) start--
    while (end < text.length && text[end].isWhitespace()) end++
    return TextRange(start, end)
  }

  class Problem(val match: RuleMatch, lang: Lang, text: TextContent, private val testDescription: Boolean)
    : TextProblem(LanguageToolRule(lang, match.rule), text, TextRange(match.fromPos, match.toPos)) {

    override fun getShortMessage(): String =
      match.shortMessage.trimToNull() ?: match.rule.description.trimToNull() ?: match.rule.category.name

    override fun getDescriptionTemplate(isOnTheFly: Boolean): String =
      if (testDescription) match.rule.id
      else match.messageSanitized

    override fun getTooltipTemplate(): String = toTooltipTemplate(match)

    override fun getSuggestions(): List<Suggestion> = match.suggestedReplacements.map { Suggestion.replace(highlightRanges[0], it) }

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

    override fun isStyleLike(): Boolean {
      return LanguageToolRule.isStyleLike(match.rule)
    }
  }
}

private val logger = LoggerFactory.getLogger(LanguageToolChecker::class.java)
private val interner = Interner.createWeakInterner<String>()
private val sentenceSeparationRules = setOf("LC_AFTER_PERIOD", "PUNT_GEEN_HL", "KLEIN_NACH_PUNKT")
private val openClosedRangeStart = Regex("[\\[(].+?(\\.\\.|:|,|;).+[])]")
private val openClosedRangeEnd = Regex(".*" + openClosedRangeStart.pattern)
private val quotedLiteralPattern = Regex("['\"]\\S+['\"]")

internal fun grammarRules(tool: JLanguageTool, lang: Lang): List<LanguageToolRule> {
  return tool.allRules.asSequence()
    .filter { !it.isDictionaryBasedSpellingRule }
    .groupBy { it.id }
    .map { (_, rules) -> LanguageToolRule(lang, rules.first(), rules) }
}

/**
 * Git adds "cherry picked from", which doesn't seem entirely grammatical,
 * but zillions of tools depend on this message, and it's unlikely to be changed.
 * So we ignore this pattern in commit messages and literals (which might be used for parsing git output)
 */
private fun isGitCherryPickedFrom(match: RuleMatch, text: TextContent): Boolean {
  return match.rule.id == "EN_COMPOUNDS_CHERRY_PICKED" && match.fromPos > 0 && text.startsWith("(cherry picked from", match.fromPos - 1) &&
         (text.domain == TextContent.TextDomain.LITERALS ||
          text.domain == TextContent.TextDomain.PLAIN_TEXT && runReadAction { CommitMessage.isCommitMessage(text.containingFile) })
}

private fun isKnownLTBug(match: RuleMatch, text: TextContent): Boolean {
  if (match.rule is EnglishUnpairedQuotesRule) {
    if (match.fromPos > 0 &&
        (text.startsWith("\")", match.fromPos - 1) || text.subSequence(0, match.fromPos).contains("(\""))) {
      return true //https://github.com/languagetool-org/languagetool/issues/5269
    }
    if (text.startsWith("'", match.fromPos) && text.subSequence(match.fromPos + 1, text.length).contains("'")) {
      return true // https://github.com/languagetool-org/languagetool/issues/7249
    }
    if (match.fromPos > 1 && text.startsWith("'", match.fromPos) && text.subSequence(0, match.fromPos).count { it == '\'' } == 1) {
      return true // https://github.com/languagetool-org/languagetool/issues/11379
    }
    if (text.substring(match.fromPos, match.toPos) == "\"" && text.subSequence(0, match.fromPos).contains("\"")) {
      return true // e.g. commented raise ValueError(f"a very long text so that the vicinity of the error doesn't seem like code")
    }
  }

  if (match.rule is GenericUnpairedBracketsRule) {
    if (couldBeOpenClosedRange(text, match.fromPos)) {
      return true
    }

    if (isPartOfQuotedLiteralText(match, text)) {
      return true
    }
  }

  if (match.rule.id == "ARTICLE_ADJECTIVE_OF" && text.substring(match.fromPos, match.toPos).equals("iterable", ignoreCase = true)) {
    return true // https://github.com/languagetool-org/languagetool/issues/5270
  }

  if (match.rule.id.endsWith("DOUBLE_PUNCTUATION") &&
      (isNumberRange(match.fromPos, match.toPos, text) || isPathPart(match.fromPos, match.toPos, text))) {
    return true
  }

  if (match.rule.id == "A_RB_NN" &&
      text.substring(match.fromPos, match.toPos).equals("finally block", ignoreCase = true) &&
      (text.domain == TextContent.TextDomain.DOCUMENTATION || text.domain == TextContent.TextDomain.COMMENTS)) {
    return true // https://github.com/languagetool-org/languagetool/issues/9511
  }

  if (match.rule.fullId == "UP_TO_DATE_HYPHEN[1]") {
    return true // https://github.com/languagetool-org/languagetool/issues/8285
  }

  return false
}

private val RuleMatch.messageSanitized
  get() = message.replace("<suggestion>", "").replace("</suggestion>", "")

private fun isPartOfQuotedLiteralText(match: RuleMatch, text: TextContent): Boolean {
  return quotedLiteralPattern.findAll(text.toString())
    .map { TextRange(it.range.first, it.range.last) }
    .any { it.intersectsStrict(match.fromPos, match.toPos) }
}

// https://github.com/languagetool-org/languagetool/issues/6566
private fun couldBeOpenClosedRange(text: TextContent, index: Int): Boolean {
  val unpaired = text[index]
  return "([".contains(unpaired) && openClosedRangeStart.matchesAt(text, index) ||
         ")]".contains(unpaired) && openClosedRangeEnd.matches(text.subSequence(0, index + 1))
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
    p {
      +match.messageSanitized
    }

    p {
      style = "text-align: left; font-size: x-small; color: gray; padding-top: 10px; padding-bottom: 0px;"
      +" "
      +GrazieBundle.message("grazie.tooltip.powered-by-language-tool")
    }
  }
  return interner.intern(html)
}
