@file:Suppress("NonAsciiCharacters")

package com.intellij.grazie.text

import ai.grazie.gec.model.problem.ActionSuggestion
import ai.grazie.gec.model.problem.Problem
import ai.grazie.gec.model.problem.ProblemFix
import ai.grazie.gec.model.problem.SuppressableKind
import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.LanguageISO
import ai.grazie.rules.Example
import ai.grazie.rules.MatchingResult
import ai.grazie.rules.NodeRuleMatch
import ai.grazie.rules.RuleMatch
import ai.grazie.rules.document.Delimiter
import ai.grazie.rules.document.DocumentRule
import ai.grazie.rules.document.DocumentSentence
import ai.grazie.rules.settings.RuleSetting
import ai.grazie.rules.settings.Setting
import ai.grazie.rules.settings.TextStyle
import ai.grazie.rules.toolkit.LanguageToolkit
import ai.grazie.rules.tree.Parameter
import ai.grazie.rules.tree.Tree
import ai.grazie.rules.tree.Tree.ParameterValues
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.detection.toAvailableLang
import com.intellij.grazie.ide.inspection.ai.RephraseAction
import com.intellij.grazie.ide.inspection.auto.AutoFix
import com.intellij.grazie.ide.ui.configurable.StyleConfigurable.Companion.ruleEngineLanguages
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.rule.ParsedSentence
import com.intellij.grazie.rule.RuleIdeClient
import com.intellij.grazie.rule.SentenceBatcher
import com.intellij.grazie.rule.SentenceTokenizer
import com.intellij.grazie.style.ConfigureSuggestedParameter
import com.intellij.grazie.style.TextLevelFix
import com.intellij.grazie.text.TextContent.TextDomain
import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.grazie.utils.Text
import com.intellij.grazie.utils.TextStyleDomain
import com.intellij.grazie.utils.getTextDomain
import com.intellij.grazie.utils.ijRange
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.AttachmentFactory
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPlainTextFile
import com.intellij.util.ExceptionUtil
import com.intellij.util.text.StringOperation
import org.jetbrains.annotations.ApiStatus
import java.net.URL
import java.util.SequencedMap

typealias TreeRange = ai.grazie.rules.tree.TextRange

class TreeRuleChecker private constructor() {
  companion object {
    private val LOG = Logger.getInstance(TreeRuleChecker::class.java)

    const val EN_STYLE_CATEGORY: String = "Style"

    private val punctuationCategories = mapOf(
      "en" to "Punctuation",
      "ru" to "Пунктуация",
      "uk" to "Пунктуація",
      "de" to "Interpunktion",
    )
    private val grammarCategories = mapOf(
      "en" to "Grammar",
      "ru" to "Грамматика",
      "uk" to "Граматика",
      "de" to "Grammatik",
    )
    private val styleCategories = mapOf(
      "en" to EN_STYLE_CATEGORY,
      "ru" to "Стиль",
      "uk" to "Стиль",
      "de" to "Stil",
    )
    private val semanticCategories = mapOf(
      "en" to "Semantics",
      "ru" to "Логические ошибки",
      "uk" to "Логічні помилки",
      "de" to "Semantische Unstimmigkeiten",
    )
    private val typographyCategories = mapOf(
      "en" to "Typography",
      "ru" to "Типографика",
      "uk" to "Типографія",
      "de" to "Typografie",
    )
    private val spellingCategories = mapOf(
      "en" to "Possible Typo",
      "ru" to "Проверка орфографии",
      "uk" to "Орфографія",
      "de" to "Mögliche Tippfehler",
    )

    @Suppress("unused")
    @ApiStatus.Internal
    const val SMART_APOSTROPHE: String = "Grazie.RuleEngine.En.Typography.SMART_APOSTROPHE"

    @JvmStatic
    fun getRules(language: Language): List<Rule> {
      if (language !in ruleEngineLanguages || SentenceBatcher.findInstalledLTLanguage(language) == null) {
        return emptyList()
      }

      val toolkit = LanguageToolkit.forLanguage(language)
      return toolkit.publishedRules().map(::toGrazieRule)
    }

    @JvmStatic
    fun toGrazieRule(rule: ai.grazie.rules.Rule): Rule {
      val langCode = rule.language().iso.toString()
      val category = when {
        rule.id.startsWith("Style.") -> styleCategories[langCode]
        rule.id.startsWith("Typography.") -> typographyCategories[langCode]
        rule.id.startsWith("Punctuation.") -> punctuationCategories[langCode]
        rule.id.startsWith("Semantics.") -> semanticCategories[langCode]
        rule.id.startsWith("Spelling.") -> spellingCategories[langCode]
        else -> grammarCategories[langCode]
      }!!
      val categories = if (rule.isStyleLike) listOf(styleCategories[langCode]!!) else listOf(category)

      return object : Rule(rule.globalId(), rule.language(), rule.displayName, categories.first()) {
        override fun getCategories(): List<String> = categories

        override fun getDescription(): String {
          val examples = rule.getExamples(RuleIdeClient.INSTANCE)
          return buildString {
            append(rule.getDescription(RuleIdeClient.INSTANCE))
            append("<br><br>")
            if (examples.isNotEmpty()) {
              append("<p style='padding-bottom:5px;'>")
              append(GrazieBundle.message("grazie.settings.grammar.rule.examples"))
              append("</p>")
              append("<table style='width:100%;' cellspacing=0 cellpadding=0>\n")
              for (example in examples) {
                val corrections = example.correctedTexts().joinToString(separator = "") { "$it<br>" }
                append(renderExampleRow(example, corrections))
              }
              append("</table><br/>")
            }

            if (langCode != "en") {
              append(GrazieBundle.message("grazie.settings.grammar.cloud.only.rule"))
              append("<br><br>")
            }
          }
        }

        override fun getUrl(): URL? = rule.url
        override fun getFeaturedSetting(): Setting = RuleSetting(rule)
        override fun isEnabledByDefault(domain: TextStyleDomain): Boolean =
          rule.isRuleEnabledByDefault(GrazieConfig.get().getTextStyle(domain), RuleIdeClient.INSTANCE)

      }
    }

    private fun renderExampleRow(example: Example, corrections: String): String {
      return buildString {
        append("<tr><td valign='top' style='padding-bottom: 5px; padding-right: 5px; color: gray;'>")
        append(GrazieBundle.message("grazie.settings.grammar.rule.incorrect"))
        append("&nbsp;</td>")
        append("<td style='padding-bottom: 5px; width: 100%'>")
        append(GrazieProblem.visualizeSpace(example.errorText()))
        append("</td></tr>")

        if (corrections.isNotEmpty()) {
          append("<tr><td valign='top' style='padding-bottom: 10px; padding-right: 5px; color: gray;'>")
          append(GrazieBundle.message("grazie.settings.grammar.rule.correct"))
          append("</td>")
          append("<td style='padding-bottom: 10px; width: 100%;'>")
          append(GrazieProblem.visualizeSpace(corrections))
          append("</td></tr>")
        }
      }
    }

    private fun doCheck(text: TextContent, sentences: List<ParsedSentence>): List<MatchingResult> {
      if (sentences.isEmpty()) return emptyList()

      try {
        val parameters = calcParameters(sentences)
        val trees = sentences.map { it.tree.withParameters(parameters) }
        val rules = enabledRules(trees.first(), text)
        return matchTrees(trees, rules)
      }
      catch (e: Throwable) {
        val cause = ExceptionUtil.getRootCause(e)
        if (cause is ProcessCanceledException) {
          throw cause
        }
        throw e
      }
    }

    private fun enabledRules(sampleTree: Tree, content: TextContent): List<ai.grazie.rules.Rule> {
      val language = sampleTree.treeSupport().grazieLanguage
      val toolkit = LanguageToolkit.forLanguage(language)
      val flat = sampleTree.isFlat
      return toolkit.publishedRules().filter { rule ->
        (!flat || rule.supportsFlatTrees()) && toGrazieRule(rule).isCurrentlyEnabled(content)
      }
    }

    private fun matchTrees(trees: List<Tree>, rules: List<ai.grazie.rules.Rule>): List<MatchingResult> {
      return trees.map { tree ->
        val matchingResults = ArrayList<MatchingResult>(rules.size)
        for (rule in rules) {
          ProgressManager.checkCanceled()
          matchingResults.add(rule.match(tree))
        }
        MatchingResult.concat(matchingResults)
      }
    }

    private fun calcParameters(sentences: List<ParsedSentence>): ParameterValues {
      val parameters = HashMap<String, String>()
      val language = sentences.first().tree.treeSupport().grazieLanguage
      val content = sentences.first().extractedText
      val toolkit = LanguageToolkit.forLanguage(language)
      toolkit.allParameters(RuleIdeClient.INSTANCE).forEach { parameter ->
        parameters[parameter.id()] = getParamValue(parameter, language, content)
      }
      val variant = getLanguageVariant(language)
      if (variant != null) {
        parameters[Parameter.LANGUAGE_VARIANT] = variant
      }
      return ParameterValues(parameters)
    }

    private fun getParamValue(param: Parameter, language: Language, content: TextContent): String {
      val domain = content.getTextDomain()
      val value = GrazieConfig.get().paramValue(domain, language, param)
      if (value == null || param.possibleValues(RuleIdeClient.INSTANCE).none { value == it.id() }) {
        val textStyle = TextStyle.styles(RuleIdeClient.INSTANCE).firstOrNull { it.id() == domain.name }
                        ?: GrazieConfig.get().getTextStyle()
        return param.defaultValue(textStyle, RuleIdeClient.INSTANCE).id()
      }
      return value
    }

    private fun getLanguageVariant(language: Language): String? {
      val lang = HighlightingUtil.findInstalledLang(language) ?: return null
      return getLanguageVariant(lang)
    }

    @JvmStatic
    fun getLanguageVariant(lang: Lang): String? {
      val ltLanguage = lang.jLanguage ?: return null
      if (lang.iso == LanguageISO.EN || lang.iso == LanguageISO.DE) {
        val countries = ltLanguage.countries
        if (countries.isNotEmpty()) {
          var variant = countries[0]
          if (lang.iso == LanguageISO.EN && variant == "GB" && GrazieConfig.get().useOxfordSpelling) {
            variant = ChangeLanguageVariant.BRITISH_OXFORD_ID
          }
          return variant
        }
      }
      return null
    }

    /**
     * @deprecated Use {@link TreeRuleChecker#checkText(List)} instead
     */
    @Suppress("unused")
    @Deprecated("Use checkText(List) instead")
    @JvmStatic
    fun checkTextLevelProblems(file: PsiFile): List<TreeProblem> {
      return checkText(ParsedSentence.getAllCheckedSentences(file.viewProvider))
    }

    private fun checkDocumentProblems(file: PsiFile, doc: List<SentenceWithContent>): List<TreeProblem> {
      if (doc.isEmpty()) return emptyList()

      val result = documentProblems(file, doc)
      val ignored = result.filter { findIgnoringFilter(it) != null }
      if (ignored.isEmpty()) {
        return result
      }

      val docIgnoredRanges = ignored.mapTo(LinkedHashSet()) { spanRanges(it.match.reportedRanges()) }
      val secondPassResult = documentProblems(file, doc.map { sentenceWithContent ->
        SentenceWithContent(
          sentenceWithContent.sentence.withSuppressions(
            union(
              sentenceWithContent.sentence.suppressions,
              sentenceRanges(docIgnoredRanges, sentenceWithContent.sentence, sentenceWithContent.docSentenceOffset),
            ),
          ),
          sentenceWithContent.content,
          sentenceWithContent.contentStart,
          sentenceWithContent.docSentenceOffset,
        )
      })
      return secondPassResult.filter { findIgnoringFilter(it) == null }
    }

    private fun union(firstSet: Set<TreeRange>, secondSet: Set<TreeRange>): Set<TreeRange> =
      LinkedHashSet(firstSet).apply { addAll(secondSet) }

    private fun spanRanges(ranges: List<TreeRange>): TreeRange {
      val first = ranges.first()
      var start = first.start()
      var end = first.end()
      for (range in ranges.drop(1)) {
        start = minOf(start, range.start())
        end = maxOf(end, range.end())
      }
      return TreeRange.fromLength(start, end - start)
    }

    private fun sentenceRanges(
      docRanges: Set<TreeRange>,
      sentence: DocumentSentence,
      sentenceOffset: Int,
    ): Set<TreeRange> {
      val sentenceRange = TextRange.from(sentenceOffset, sentence.text.length)
      return docRanges.asSequence()
        .filter { sentenceRange.containsRange(it.start(), it.end()) }
        .map { it.shiftLeft(sentenceRange.startOffset) }
        .toCollection(LinkedHashSet())
    }

    private fun documentProblems(file: PsiFile, doc: List<SentenceWithContent>): List<TreeProblem> {
      val docText = doc.joinToString(separator = "") { it.sentence.text }
      val result = ArrayList<TreeProblem>()

      val matchingResult = checkDocument(doc)
      for (match in matchingResult.matches) {
        val reportedRanges = match.reportedRanges()
        val firstRange = reportedRanges.first()
        val sentence = findSentence(doc, firstRange.start(), firstRange.end())
        val content = sentence.content
        val fixes = asciiAwareFixes(match, content, docText) ?: continue

        val source = GrazieProblem.copyWithFixes(match.toProblem(RuleIdeClient.INSTANCE), emptyList())
        val problem = TreeProblem(source.withOffset(-sentence.contentStart), match, content)

        result.add(
          problem.withCustomFixes(fixes.map { fix ->
            TextLevelFix(file, GrazieProblem.getQuickFixText(fix), fileLevelChanges(fix, doc))
          }),
        )
      }
      return result
    }

    private fun fileLevelChanges(fix: ProblemFix, doc: List<SentenceWithContent>): List<StringOperation> {
      return fix.changes.map { change ->
        val sentence = findSentence(doc, change.range.start, change.range.endExclusive)
        StringOperation.replace(
          sentence.content.textRangeToFile(change.ijRange().shiftLeft(sentence.contentStart)),
          change.text,
        )
      }
    }

    private fun findSentence(doc: List<SentenceWithContent>, docStart: Int, docEnd: Int): SentenceWithContent {
      ProgressManager.checkCanceled()
      val sentence = doc.lastOrNull {
        it.docSentenceOffset <= docStart && docEnd <= it.docSentenceOffset + it.sentence.text.length
      }
      assert(sentence != null)
      return sentence!!
    }

    private fun checkDocument(doc: List<SentenceWithContent>): MatchingResult {
      val rules = LinkedHashMap<Language, List<ai.grazie.rules.Rule>>()
      for (sentenceWithContent in doc) {
        val language = sentenceWithContent.sentence.language
        if (!rules.containsKey(language) && language in ruleEngineLanguages) {
          rules[language] = enabledRules(sentenceWithContent.sentence.treeOrThrow(), sentenceWithContent.content)
        }
      }

      val sentences = doc.map(SentenceWithContent::sentence)
      return MatchingResult.concat(
        rules.values
          .flatten()
          .filter { it.isStyleLike }
          .filterIsInstance<DocumentRule>()
          .map { rule ->
            ProgressManager.checkCanceled()
            rule.checkDocument(sentences)
          },
      )
    }

    private fun suppressedRanges(): Map<String, Set<TreeRange>> {
      val result = HashMap<String, MutableSet<TreeRange>>()
      var counter = 0
      for (pattern in GrazieConfig.get().suppressingContext.suppressed) {
        val sep = pattern.indexOf('|')
        if (sep < 2) continue

        val fragment = pattern.substring(0, sep)
        val sentence = pattern.substring(sep + 1)
        var index = -1
        while (true) {
          if (counter++ % 1024 == 0) ProgressManager.checkCanceled()

          index = sentence.indexOf(fragment, index + 1)
          if (index < 0) break

          result.computeIfAbsent(sentence) { LinkedHashSet() }.add(TreeRange.fromLength(index, fragment.length))
        }
      }
      return result
    }

    @JvmStatic
    suspend fun checkText(texts: List<TextContent>): List<TreeProblem> {
      return checkText(ParsedSentence.getAllCheckedSentences(texts))
    }

    private fun checkText(textToSentences: SequencedMap<TextContent, List<ParsedSentence>>): List<TreeProblem> {
      if (textToSentences.isEmpty()) return emptyList()

      val suppressedRanges = suppressedRanges()
      val problems = ArrayList<TreeProblem>()
      val doc = ArrayList<SentenceWithContent>()
      var offset = 0
      for ((content, sentences) in textToSentences.entries) {
        if (sentences.isEmpty()) continue

        var offsetInContent = 0

        val matches = doCheck(content, sentences)
        val matchProblems = checkPlainProblems(content, matches)
        AutoFix.consider(content, matchProblems)
        problems.addAll(matchProblems)

        for (i in sentences.indices) {
          val parsed = sentences[i]

          val untrimmedRange = parsed.untrimmedRange
          val untrimmedText = untrimmedRange.substring(content.toString())

          val trimmed = untrimmedText.trim()
          val trimmedStart = untrimmedText.indexOf(trimmed)
          val suppressions = (suppressedRanges[trimmed] ?: emptySet())
            .mapTo(LinkedHashSet()) { it.shiftRight(trimmedStart) }

          val sentence = DocumentSentence(untrimmedText, parsed.tree.treeSupport().grazieLanguage)
            .withIntro(if (i == 0) getIntro(content) else emptyList())
            .withExclusions(SentenceTokenizer.rangeExclusions(content, untrimmedRange))
            .withSuppressions(suppressions)
            .withTree(parsed.tree.withStartOffset(offset))
            .withMetadata(matches[i].metadata)
          doc.add(SentenceWithContent(sentence, content, offset, offset + untrimmedRange.startOffset))
          offsetInContent += sentence.text.length
        }
        offset += offsetInContent
      }

      val file = textToSentences.keys.first().containingFile
      problems.addAll(checkDocumentProblems(file, doc))

      return problems
    }

    private data class SentenceWithContent(
      val sentence: DocumentSentence.Analyzed,
      val content: TextContent,
      val contentStart: Int,
      val docSentenceOffset: Int,
    )

    private fun getIntro(content: TextContent): List<Delimiter> {
      val intros = mutableListOf(Delimiter.fragmentBoundary)
      when (content.domain) {
        TextDomain.COMMENTS -> intros.add(Delimiter.codeCommentStart)
        TextDomain.DOCUMENTATION -> intros.add(Delimiter.codeDocumentationStart)
        TextDomain.LITERALS -> intros.add(Delimiter.stringLiteralStart)
        TextDomain.PLAIN_TEXT -> Unit
      }
      return intros
    }

    private fun findIgnoringFilter(problem: TreeProblem): ProblemFilter? {
      return ProblemFilter.allIgnoringFilters(problem).findFirst().orElse(null)
    }

    /**
     * @deprecated Use {@link TreeRuleChecker#checkText(List)} instead
     */
    @Suppress("unused")
    @Deprecated("Use checkText(List) instead")
    @JvmStatic
    fun check(text: TextContent, sentences: List<ParsedSentence>): List<TreeProblem> {
      val matchingResults = doCheck(text, sentences)
      val problems = checkPlainProblems(text, matchingResults)
      AutoFix.consider(text, problems)
      return problems
    }

    private fun checkPlainProblems(text: TextContent, matchingResults: List<MatchingResult>): List<TreeProblem> {
      val problems = ArrayList<TreeProblem>()
      for (match in matchingResults.flatMap { it.matches }) {
        ProgressManager.checkCanceled()
        if (match is NodeRuleMatch && touchesUnknownFragments(text, match.result().touchedRange(), match.rule())) {
          continue
        }

        createProblem(text, match)?.let(problems::add)
      }
      return problems
    }

    private fun createProblem(text: TextContent, match: RuleMatch): TreeProblem? {
      val rule = match.rule()
      if (shouldSuppressByPlace(rule, text)) {
        return null
      }

      val fixes = asciiAwareFixes(match, text, text.toString()) ?: return null
      if (rule.language() == Language.ENGLISH &&
          rule.id == "Typography.VARIANT_QUOTE_PUNCTUATION" &&
          text.domain != TextDomain.PLAIN_TEXT &&
          match.reportedRanges().size == 1 &&
          match.reportedRanges().first().substring(text.toString()).matches(Regex(".*['\"]\\p{P}"))) {
        return null
      }

      return TreeProblem(GrazieProblem.copyWithFixes(match.toProblem(RuleIdeClient.INSTANCE), fixes), match, text)
    }

    private fun asciiAwareFixes(match: RuleMatch, content: TextContent, fullText: String): List<ProblemFix>? {
      if (!isAsciiContext(content)) return match.problemFixes()
      if (match.rule().id.endsWith(".ASCII_APPROXIMATIONS")) {
        return null
      }
      try {
        return match.asciiContextFixes(fullText)
      }
      catch (e: StringIndexOutOfBoundsException) {
        throw RuntimeExceptionWithAttachments(e, toAttachment(content, fullText))
      }
    }

    private fun toAttachment(content: TextContent, fullText: String): Attachment {
      val file = content.containingFile
      return AttachmentFactory.createContext(
        "File type: ${file.viewProvider.virtualFile.fileType}\n" +
        "File language: ${file.language}\n" +
        "File name: ${file.name}\n" +
        "Content: $fullText",
      )
    }

    private fun isAsciiContext(text: TextContent): Boolean {
      return text.domain != TextDomain.PLAIN_TEXT || text.containingFile is PsiPlainTextFile
    }

    private fun shouldSuppressByPlace(rule: ai.grazie.rules.Rule, text: TextContent): Boolean {
      val domain = text.getTextDomain()
      if (domain == TextStyleDomain.Other) return false
      return domain.textStyle.disabledRules().contains(rule.globalId())
    }

    private fun touchesUnknownFragments(text: TextContent, range: TreeRange, rule: ai.grazie.rules.Rule): Boolean {
      val ruleRangeInText = range.ijRange()
      if (ruleRangeInText.endOffset > text.length) {
        LOG.error(
          "Invalid match range $ruleRangeInText for rule $rule in a text of length ${text.length}",
          Attachment("text.txt", text.toString()),
        )
        return true
      }
      return text.hasUnknownFragmentsIn(Text.expandToTouchWords(text, ruleRangeInText))
    }
  }

  class TreeProblem : GrazieProblem {
    @JvmField
    val match: RuleMatch
    private val customFixes: List<LocalQuickFix>
    private val domain: TextStyleDomain

    constructor(problem: Problem, match: RuleMatch, text: TextContent) :
      this(problem, toGrazieRule(match.rule()), text, match, emptyList())

    private constructor(
      problem: Problem,
      ideaRule: Rule,
      text: TextContent,
      match: RuleMatch,
      customFixes: List<LocalQuickFix>,
    ) : super(problem, ideaRule, text) {
      this.match = match
      this.customFixes = customFixes
      domain = text.getTextDomain()
    }

    @Suppress("HardCodedStringLiteral")
    override fun getDescriptionTemplate(isOnTheFly: Boolean): @InspectionMessage String {
      if (ApplicationManager.getApplication().isUnitTestMode) return match.rule().globalId()
      return super.getDescriptionTemplate(isOnTheFly)
    }

    override fun getCustomFixes(): List<LocalQuickFix> {
      val actionSuggestions = source.actionSuggestions ?: return customFixes
      val suggestionFixes = actionSuggestions.mapNotNull { suggestion ->
        when (suggestion) {
          is ActionSuggestion.ChangeParameter -> {
            if (suggestion.parameterId.endsWith(Parameter.LANGUAGE_VARIANT)) {
              ChangeLanguageVariant.create(
                match.rule().language(),
                suggestion.suggestedValue!!.id,
                suggestion.quickFixText,
              )
            }
            else {
              ConfigureSuggestedParameter(suggestion, domain, match.rule().language().toAvailableLang(), suggestion.quickFixText)
            }
          }
          ActionSuggestion.RephraseAround -> RephraseAction()
          else -> null
        }
      }
      return customFixes + suggestionFixes
    }

    override fun fitsGroup(group: RuleGroup): Boolean {
      val rules = group.rules
      val kind = source.suppressableKind
      if (RuleGroup.INCOMPLETE_SENTENCE in rules && kind == SuppressableKind.INCOMPLETE_SENTENCE) {
        return true
      }
      if (RuleGroup.SENTENCE_START_CASE in rules && kind == SuppressableKind.UPPERCASE_SENTENCE_START) {
        return true
      }
      if (RuleGroup.SENTENCE_END_PUNCTUATION in rules && kind == SuppressableKind.UNFINISHED_SENTENCE) {
        return true
      }
      if (RuleGroup.UNDECORATED_SENTENCE_SEPARATION in rules && kind == SuppressableKind.UNDECORATED_SENTENCE_SEPARATION) {
        return true
      }
      if (RuleGroup.UNLIKELY_OPENING_PUNCTUATION in rules && kind == SuppressableKind.UNLIKELY_OPENING_PUNCTUATION) {
        return true
      }

      return super.fitsGroup(group)
    }

    fun withCustomFixes(fixes: List<LocalQuickFix>): TreeProblem = TreeProblem(source, rule, text, match, customFixes + fixes)
    override fun isStyleLike(): Boolean = match.rule().isStyleLike
    override fun shouldSuppressInCodeLikeFragments(): Boolean = match.rule().shouldSuppressInCodeLikeFragments()

    override fun copyWithProblemFixes(fixes: List<ProblemFix>): TreeProblem =
      TreeProblem(copyWithFixes(source, fixes), rule, text, match, customFixes)

    override fun copyWithHighlighting(always: Array<ai.grazie.text.TextRange>, onHover: Array<ai.grazie.text.TextRange>): GrazieProblem =
      TreeProblem(copyWithHighlighting(source, always, onHover), rule, text, match, customFixes)

    override fun copyWithInfoAndMessage(info: Problem.KindInfo, message: String): GrazieProblem =
      TreeProblem(copyWithInfoAndMessage(source, info, message), rule, text, match, customFixes)
  }
}
