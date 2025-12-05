package com.intellij.grazie.spellcheck

import ai.grazie.spell.text.Typo
import ai.grazie.utils.LinkedSet
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection.Companion.sortByPriority
import com.intellij.grazie.text.ProblemFilter
import com.intellij.grazie.text.TextContent.TextDomain
import com.intellij.grazie.text.TextExtractor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy
import java.util.function.Consumer

object GrazieTextLevelSpellCheckingExtension {

  /**
   * Performs spell-checking on the specified PSI element.
   *
   * The implementation may examine neighboring elements of [PsiElement] if needed.
   * In case of doing that, it's implementation's responsibility to not check the same element for spelling mistake twice.
   *
   * @param element The PSI element to check for spelling errors
   * @param strategy The element's spellchecking strategy
   * @param consumer The callback function that will be invoked for each spelling error detected during the inspection
   *
   * @return [SpellCheckingResult.Checked] if the PSI element has been checked, [SpellCheckingResult.Ignored] otherwise
   */
  fun spellcheck(element: PsiElement, strategy: SpellcheckingStrategy, session: LocalInspectionToolSession, consumer: Consumer<SpellingTypo>): SpellCheckingResult {
    if (!strategy.useTextLevelSpellchecking()) return SpellCheckingResult.Ignored
    ProgressManager.checkCanceled()

    val texts = sortByPriority(TextExtractor.findTextsExactlyAt(element, TextDomain.ALL), session.priorityRange)
    if (texts.isEmpty()) return SpellCheckingResult.Ignored

    val filteredTexts = texts.filter { ProblemFilter.allIgnoringFilters(it).findAny().isEmpty }
    if (filteredTexts.isEmpty()) return SpellCheckingResult.Checked

    filteredTexts
      .map { SpellingCheckerRunner(it) }
      .map { checker -> checker to checker.run() }
      .flatMap { (checker, typos) ->
        typos
          .filter { checker.belongsToElement(it, element) }
          .map { checker.updateRanges(it, element) }
      }
      .map { toSpellingTypo(it, element) }
      .forEach { consumer.accept(it) }
    return SpellCheckingResult.Checked
  }

  private fun toSpellingTypo(typo: Typo, element: PsiElement): SpellingTypo {
    return createTypo(typo.word, mapRange(typo.range), element) {
      if (typo is CloudTypo) typo.fixes else LinkedSet()
    }
  }

  private fun mapRange(range: ai.grazie.text.TextRange): TextRange = TextRange(range.start, range.endExclusive)

  private fun createTypo(word: String, range: TextRange, element: PsiElement, lazyFixes: () -> LinkedSet<String>) = object : SpellingTypo {
    override val word: String = word
    override val range: TextRange = range
    override val element: PsiElement = element
    override val fixes: LinkedSet<String> = lazyFixes()
  }
}

/** A typo detected by [GrazieTextLevelSpellCheckingExtension] in a sentence or text inside a [PsiElement]. */
interface SpellingTypo {
  /** The misspelled word inside the [element] */
  val word: String

  /** The range of the typo in the [element]'s text */
  val range: TextRange

  /** Element that contains a misspelled [word] within the given text [range] */
  val element: PsiElement

  /** Suggested corrections for the [word], possibly calculated lazily */
  val fixes: LinkedSet<String>
}

enum class SpellCheckingResult { Checked, Ignored }