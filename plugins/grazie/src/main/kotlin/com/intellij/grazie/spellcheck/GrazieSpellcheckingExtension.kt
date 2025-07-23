package com.intellij.grazie.spellcheck

import ai.grazie.nlp.langs.LanguageWithVariant
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
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil.BombedCharSequence
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.spellchecker.grazie.GrazieSpellCheckerEngine
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.spellchecker.inspections.SpellCheckingInspection.SpellCheckingScope.Code
import com.intellij.spellchecker.inspections.SpellcheckingExtension
import com.intellij.spellchecker.inspections.SpellcheckingExtension.SpellCheckingResult
import com.intellij.spellchecker.inspections.SpellcheckingExtension.SpellingTypo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Consumer


class GrazieSpellcheckingExtension : SpellcheckingExtension {

  override fun spellcheck(element: PsiElement, session: LocalInspectionToolSession, consumer: Consumer<SpellingTypo>): SpellCheckingResult {
    if (!Registry.`is`("spellchecker.grazie.enabled", false)) return SpellCheckingResult.Ignored
    ProgressManager.checkCanceled()
    if (element is PsiWhiteSpace) return SpellCheckingResult.Checked

    val texts = sortByPriority(TextExtractor.findTextsAt(element, allDomains()), session.priorityRange)
    val textSpeller = getTextSpeller(element.project) ?: return SpellCheckingResult.Ignored
    if (texts.isNotEmpty()) {
      texts
        .map { it to findTypos(it, session, textSpeller) }
        .flatMap { mapTypo(it.first, it.second) }
        .filter { belongsToPsiElement(element, it) }
        .forEach { consumer.accept(it) }
      return SpellCheckingResult.Checked
    }

    val strategy = SpellCheckingInspection.getSpellcheckingStrategy(element)
    if (strategy.elementFitsScope(element, setOf(Code))) return SpellCheckingResult.Ignored

    val range = ElementManipulators.getManipulator(element)?.getRangeInElement(element) ?: TextRange(0, element.textLength)
    val text = range.substring(element.text)
    textSpeller.checkText(text)
      .map { SimpleTypo(it.word, mapRange(it.range).shiftRight(range.startOffset), element) }
      .forEach { consumer.accept(it) }
    return SpellCheckingResult.Checked
  }

  private fun getTextSpeller(project: Project): TextSpeller? {
    val speller = project.service<GrazieSpellCheckerEngine>().speller ?: return null
    return TextSpeller(listOf(object : Speller by speller {
      override fun languages(): List<LanguageWithVariant> = GrazieConfig.get().enabledLanguages.mapNotNull { it.withVariant }
    }))
  }

  private fun belongsToPsiElement(element: PsiElement, typo: SpellingTypo): Boolean {
    return typo.range.intersectsStrict(TextRange(0, element.text.length))
  }

  private fun allDomains(): Set<TextContent.TextDomain> = TextContent.TextDomain.entries.toSet()

  private fun mapTypo(text: TextContent, typos: List<Typo>): List<SpellingTypo> {
    val range = text.commonParent.textRange
    return typos.map {
      SimpleTypo(
        it.word,
        text.textRangeToFile(mapRange(it.range)).shiftLeft(range.startOffset),
        text.commonParent
      )
    }
  }

  private fun mapRange(range: ai.grazie.text.TextRange): TextRange = TextRange(range.start, range.endExclusive)

  private fun findTypos(text: TextContent, session: LocalInspectionToolSession, textSpeller: TextSpeller): List<Typo> {
    var typos = session.getUserData(KEY_TYPO_CACHE)
    if (typos == null) {
      typos = ConcurrentHashMap()
      session.putUserData(KEY_TYPO_CACHE, typos)
    }
    return typos.computeIfAbsent(text) {
      textSpeller.checkText(object : BombedCharSequence(text) {
        override fun checkCanceled() {
          ProgressManager.checkCanceled()
        }
      })
    }
  }
}

private data class SimpleTypo(
  override val word: String,
  override val range: TextRange,
  override val element: PsiElement,
) : SpellingTypo

private val KEY_TYPO_CACHE = Key.create<ConcurrentMap<TextContent, List<Typo>>>("KEY_TYPO_CACHE")