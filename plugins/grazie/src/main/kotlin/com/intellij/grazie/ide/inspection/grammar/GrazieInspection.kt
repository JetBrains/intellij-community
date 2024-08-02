// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.grazie.ide.inspection.grammar

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.text.CheckerRunner
import com.intellij.grazie.text.TextChecker
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor
import com.intellij.lang.Language
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.CachedValuesManager
import com.intellij.spellchecker.ui.SpellCheckingEditorCustomization
import java.util.*

class GrazieInspection : LocalInspectionTool(), DumbAware {

  override fun getDisplayName() = GrazieBundle.message("grazie.grammar.inspection.grammar.text")

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val file = holder.file
    if (ignoreGrammarChecking(file)) {
      return PsiElementVisitor.EMPTY_VISITOR
    }

    val checkers = TextChecker.allCheckers()
    val checkedDomains = checkedDomains()
    val areChecksDisabled = getDisabledChecker(file)

    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element is PsiWhiteSpace || areChecksDisabled(element)) return

        val texts = TextExtractor.findUniqueTextsAt(element, checkedDomains)
        if (skipCheckingTooLargeTexts(texts)) return

        for (extracted in sortByPriority(texts, session.priorityRange)) {
          val runner = CheckerRunner(extracted)
          runner.run(checkers) { problem ->
            runner.toProblemDescriptors(problem, isOnTheFly).forEach(holder::registerProblem)
          }
        }
      }
    }
  }

  /**
   * Most of those methods are used in Grazie Pro.
   */
  @Suppress("CompanionObjectInExtension")
  companion object {
    private val hasSpellChecking: Boolean by lazy {
      try {
        Class.forName("com.intellij.spellchecker.ui.SpellCheckingEditorCustomization")
        true
      }
      catch (e: ClassNotFoundException) {
        false
      }
    }

    @JvmStatic
    fun findAllTextContents(vp: FileViewProvider, domains: Set<TextContent.TextDomain>): Set<TextContent> {
      val allContents: MutableSet<TextContent> = HashSet()
      for (root in vp.allFiles) {
        for (element in SyntaxTraverser.psiTraverser(root)) {
          if (element.firstChild == null) {
            allContents.addAll(TextExtractor.findTextsAt(element, domains))
          }
        }
      }
      return allContents
    }

    @JvmStatic
    fun skipCheckingTooLargeTexts(texts: List<TextContent>): Boolean {
      if (texts.isEmpty()) return false
      if (texts.sumOf { it.length } > 50_000) return true

      val allInFile = CachedValuesManager.getProjectPsiDependentCache(texts[0].containingFile) {
        findAllTextContents(it.viewProvider, TextContent.TextDomain.ALL)
      }
      val checkedDomains = checkedDomains()
      return allInFile.asSequence().filter { it.domain in checkedDomains }.sumOf { it.length } > 200_000
    }

    @JvmStatic
    fun ignoreGrammarChecking(file: PsiFile): Boolean = hasSpellChecking && isSpellCheckingDisabled(file)

    // those who disable spell-checking probably don't want grammar checks either
    private fun isSpellCheckingDisabled(file: PsiFile) = SpellCheckingEditorCustomization.isSpellCheckingDisabled(file)

    @JvmStatic
    fun checkedDomains(): Set<TextContent.TextDomain> {
      val result = EnumSet.of(TextContent.TextDomain.PLAIN_TEXT)
      if (GrazieConfig.get().checkingContext.isCheckInStringLiteralsEnabled) {
        result.add(TextContent.TextDomain.LITERALS)
      }
      if (GrazieConfig.get().checkingContext.isCheckInCommentsEnabled) {
        result.add(TextContent.TextDomain.COMMENTS)
      }
      if (GrazieConfig.get().checkingContext.isCheckInDocumentationEnabled) {
        result.add(TextContent.TextDomain.DOCUMENTATION)
      }
      return result
    }

    @JvmStatic
    fun getDisabledChecker(file: PsiFile): (PsiElement) -> Boolean {
      val fileLanguage = file.language
      val supportedLanguages = TextExtractor.getSupportedLanguages()
      val disabledLanguages = GrazieConfig.get().checkingContext.getEffectivelyDisabledLanguageIds()
      return { element ->
        var lang: Language? = element.language
        if (fileLanguage.isKindOf(lang)) lang = fileLanguage // e.g. XML PSI in HTML files
        while (lang != null && lang !in supportedLanguages) {
          lang = lang.baseLanguage
        }
        lang != null && lang.id in disabledLanguages
      }
    }

    @JvmStatic
    fun sortByPriority(texts: List<TextContent>, priorityRange: TextRange): List<TextContent> {
      return texts.sortedBy { text ->
        val textRangeInFile = text.textRangeToFile(TextRange(0, text.length))
        when {
          textRangeInFile.contains(priorityRange) -> 0
          textRangeInFile.intersects(priorityRange) -> 1
          else -> 2
        }
      }
    }
  }
}
