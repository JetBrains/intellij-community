package com.intellij.grazie.spellcheck

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection.Companion.sortByPriority
import com.intellij.grazie.text.ProblemFilter
import com.intellij.grazie.text.TextContent.TextDomain
import com.intellij.grazie.text.TextExtractor
import com.intellij.openapi.progress.ProgressManager
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
  fun spellcheck(
    element: PsiElement,
    strategy: SpellcheckingStrategy,
    session: LocalInspectionToolSession,
    consumer: Consumer<TypoProblem>,
  ): SpellCheckingResult {
    if (!strategy.useTextLevelSpellchecking()) return SpellCheckingResult.Ignored
    ProgressManager.checkCanceled()

    val texts = sortByPriority(TextExtractor.findTextsExactlyAt(element, TextDomain.ALL), session.priorityRange)
    if (texts.isEmpty()) return SpellCheckingResult.Ignored

    texts
      .filter { ProblemFilter.allIgnoringFilters(it).findAny().isEmpty }
      .flatMap { SpellingCheckerRunner(it).run() }
      .filter { SpellingCheckerRunner.belongsToElement(it, element) }
      .filter { ProblemFilter.allIgnoringFilters(it).findAny().isEmpty }
      .forEach { consumer.accept(it) }
    return SpellCheckingResult.Checked
  }
}

enum class SpellCheckingResult { Checked, Ignored }