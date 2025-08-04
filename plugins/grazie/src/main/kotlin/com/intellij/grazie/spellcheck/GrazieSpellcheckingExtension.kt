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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.spellchecker.grazie.GrazieSpellCheckerEngine
import com.intellij.spellchecker.inspections.IdentifierSplitter.MINIMAL_TYPO_LENGTH
import com.intellij.spellchecker.inspections.SpellcheckingExtension
import com.intellij.spellchecker.inspections.SpellcheckingExtension.SpellCheckingResult
import com.intellij.spellchecker.inspections.SpellcheckingExtension.SpellingTypo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Consumer

class GrazieSpellcheckingExtension : SpellcheckingExtension {

  override fun spellcheck(element: PsiElement, session: LocalInspectionToolSession, consumer: Consumer<SpellingTypo>): SpellCheckingResult {
    if (!Registry.`is`("spellchecker.grazie.enabled")) return SpellCheckingResult.Ignored
    if (element is PsiWhiteSpace) return SpellCheckingResult.Checked
    ProgressManager.checkCanceled()

    val texts = sortByPriority(TextExtractor.findTextsAt(element, allDomains()), session.priorityRange)
    if (texts.isEmpty()) return SpellCheckingResult.Ignored

    val textSpeller = getTextSpeller(element.project) ?: return SpellCheckingResult.Ignored
    texts.asSequence()
      .map { it to findTypos(it, session, textSpeller) }
      .flatMap { mapTypo(it.first, it.second) }
      .filterNot { shouldBeIgnored(it, element) }
      .filterNot { it.word.length < MINIMAL_TYPO_LENGTH }
      .forEach { consumer.accept(it) }
    return SpellCheckingResult.Checked
  }

  private fun getTextSpeller(project: Project): TextSpeller? {
    val speller = project.service<GrazieSpellCheckerEngine>().getSpeller() ?: return null
    return TextSpeller(listOf(object : Speller by speller {
      override fun languages(): List<LanguageWithVariant> = GrazieConfig.get().enabledLanguages.mapNotNull { it.withVariant }
    }))
  }

  private fun allDomains(): Set<TextContent.TextDomain> = TextContent.TextDomain.entries.toSet()

  private fun shouldBeIgnored(typo: SimpleTypo, element: PsiElement): Boolean {
    if (element.getFirstChild() != null) return true
    val psiRange = element.getTextRange()
    return !psiRange.intersectsStrict(typo.range.shiftRight(typo.text.commonParent.textRange.startOffset))
  }

  private fun mapTypo(text: TextContent, typos: List<Typo>): List<SimpleTypo> {
    val range = text.commonParent.textRange
    return typos.map {
      SimpleTypo(
        it.word,
        text.textRangeToFile(mapRange(it.range)).shiftLeft(range.startOffset),
        text
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
  val text: TextContent,
) : SpellingTypo {
  override val element: PsiElement
    get() = text.commonParent
}

private val KEY_TYPO_CACHE = Key.create<ConcurrentMap<TextContent, List<Typo>>>("KEY_TYPO_CACHE")