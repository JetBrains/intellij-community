package com.intellij.grazie.spellcheck

import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.LanguageWithVariant
import ai.grazie.nlp.tokenizer.Tokenizer
import ai.grazie.nlp.utils.checkedEndExclusive
import ai.grazie.rules.common.KnownPhrases
import ai.grazie.spell.Speller
import ai.grazie.spell.text.TextSpeller
import ai.grazie.spell.text.Typo
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection.Companion.sortByPriority
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.util.text.StringUtil.BombedCharSequence
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.spellchecker.grazie.GrazieSpellCheckerEngine
import com.intellij.spellchecker.inspections.IdentifierSplitter.MINIMAL_TYPO_LENGTH
import com.intellij.spellchecker.inspections.SpellCheckingInspection.SpellCheckingScope.Comments
import com.intellij.spellchecker.inspections.SpellCheckingInspection.SpellCheckingScope.Literals
import com.intellij.spellchecker.inspections.SpellcheckingExtension
import com.intellij.spellchecker.inspections.SpellcheckingExtension.SpellCheckingResult
import com.intellij.spellchecker.inspections.SpellcheckingExtension.SpellingTypo
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Consumer

private val DOMAINS = TextContent.TextDomain.ALL

class GrazieSpellcheckingExtension : SpellcheckingExtension {

  private val knownPhrases = ContainerUtil.createConcurrentSoftValueMap<Language, KnownPhrases>()

  override fun spellcheck(element: PsiElement, strategy: SpellcheckingStrategy, session: LocalInspectionToolSession, consumer: Consumer<SpellingTypo>): SpellCheckingResult {
    if (!strategy.useTextLevelSpellchecking()) return SpellCheckingResult.Ignored
    ProgressManager.checkCanceled()

    val texts = sortByPriority(TextExtractor.findTextsExactlyAt(element, DOMAINS), session.priorityRange)
    if (texts.isEmpty()) {
      if (hasTextAround(element, strategy)) return SpellCheckingResult.Checked
      return SpellCheckingResult.Ignored
    }

    val textSpeller = getTextSpeller(element.project) ?: return SpellCheckingResult.Ignored
    texts.asSequence()
      .map { it to findTypos(it, session, textSpeller) }
      .flatMap { mapTypo(it.first, it.second, element) }
      .filterNot { it.word.length < MINIMAL_TYPO_LENGTH }
      .forEach { consumer.accept(it) }
    return SpellCheckingResult.Checked
  }

  private fun getTextSpeller(project: Project): TextSpeller? {
    val speller = project.service<GrazieSpellCheckerEngine>().getSpeller() ?: return null
    val enabledLanguages = GrazieConfig.get().enabledLanguages.mapNotNull { it.withVariant }
    val validPhrasesTexts = ConcurrentHashMap<CharSequence, List<ai.grazie.rules.tree.TextRange>>()

    return object : TextSpeller(listOf(object : Speller by speller {
      override fun languages(): List<LanguageWithVariant> = enabledLanguages
    })) {
      override fun ignoreInContext(word: Tokenizer.Token, text: CharSequence): Boolean {
        return super.ignoreInContext(word, text) || isRangeCoveredByValidPhrase(word, text)
      }

      private fun isRangeCoveredByValidPhrase(word: Tokenizer.Token, text: CharSequence): Boolean {
        val textRanges = validPhrasesTexts.getOrPut(text) { getValidPhraseRanges(text) }
        return textRanges.any { it.start <= word.range.first && it.end <= word.range.checkedEndExclusive }
      }

      private fun getValidPhraseRanges(text: CharSequence): List<ai.grazie.rules.tree.TextRange> {
        return enabledLanguages
          .asSequence()
          .map { it.base }
          .filter { it in KnownPhrases.SUPPORTED_LANGUAGES }
          .map { lang -> knownPhrases.computeIfAbsent(lang) { KnownPhrases.forLanguage(lang) } }
          .flatMap {
            ProgressManager.checkCanceled()
            it.validPhrases(text)
          }
          .toList()
      }
    }
  }

  private fun mapTypo(text: TextContent, typos: List<Typo>, element: PsiElement): List<SimpleTypo> {
    val psiRange = element.textRange
    return typos.mapNotNull {
      val range = text.textRangeToFile(mapRange(it.range))
      if (!psiRange.contains(range)) return@mapNotNull null
      SimpleTypo(it.word, range.shiftLeft(element.textRange.startOffset), element)
    }
  }

  private fun mapRange(range: ai.grazie.text.TextRange): TextRange = TextRange(range.start, range.endExclusive)

  private fun findTypos(text: TextContent, session: LocalInspectionToolSession, textSpeller: TextSpeller): List<Typo> {
    val typos = session.getOrCreateUserData(KEY_TYPO_CACHE) { ConcurrentHashMap() }
    return typos.computeIfAbsent(text) {
      textSpeller.checkText(object : BombedCharSequence(text) {
        override fun checkCanceled() {
          ProgressManager.checkCanceled()
        }
      })
    }
  }

  private fun hasTextAround(element: PsiElement, strategy: SpellcheckingStrategy): Boolean =
    strategy.elementFitsScope(element, setOf(Literals, Comments)) && childHasText(element)

  private fun childHasText(root: PsiElement): Boolean {
    if (root.firstChild == null) return false
    for (element in SyntaxTraverser.psiTraverser(root)) {
      if (TextExtractor.findTextsExactlyAt(element, DOMAINS).isNotEmpty()) {
        return true
      }
    }
    return false
  }
}

private data class SimpleTypo(
  override val word: String,
  override val range: TextRange,
  override val element: PsiElement,
) : SpellingTypo

private val KEY_TYPO_CACHE = Key.create<ConcurrentMap<TextContent, List<Typo>>>("KEY_TYPO_CACHE")