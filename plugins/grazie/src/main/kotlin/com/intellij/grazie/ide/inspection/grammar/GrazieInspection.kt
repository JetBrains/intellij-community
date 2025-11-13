// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.grazie.ide.inspection.grammar

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.text.*
import com.intellij.grazie.text.TextExtractor.findAllTextContents
import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.spellchecker.ui.SpellCheckingEditorCustomization
import org.jetbrains.annotations.NonNls
import java.util.*

class GrazieInspection : LocalInspectionTool(), DumbAware {

  class Grammar : LocalInspectionTool(), DumbAware {
    override fun getShortName(): @NonNls String = GRAMMAR_INSPECTION

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
      return PsiElementVisitor.EMPTY_VISITOR
    }
  }

  class Style : LocalInspectionTool(), DumbAware {
    override fun getShortName(): @NonNls String = STYLE_INSPECTION

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
      return PsiElementVisitor.EMPTY_VISITOR
    }
  }

  override fun getDisplayName(): String = GrazieBundle.message("grazie.grammar.inspection.grammar.text")

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val file = holder.file
    if (ignoreGrammarChecking(file) || hasTooLowSeverity(session) || areDisabled(session)) return PsiElementVisitor.EMPTY_VISITOR

    val checkedDomains = checkedDomains()
    val areChecksDisabled = getDisabledChecker(file)

    return object : PsiElementVisitor() {
      override fun visitWhiteSpace(space: PsiWhiteSpace) {}

      override fun visitElement(element: PsiElement) {
        if (areChecksDisabled(element)) return

        val texts = TextExtractor.findUniqueTextsAt(element, checkedDomains)
        if (skipCheckingTooLargeTexts(texts)) return
        val filteredTexts = texts.filter { ProblemFilter.allIgnoringFilters(it).findAny().isEmpty }

        sortByPriority(filteredTexts, session.priorityRange)
          .map { CheckerRunner(it) }
          .map { it to it.run() }
          .forEach { (runner, problems) ->
            problems.forEach { problem ->
              runner.toProblemDescriptors(problem, holder.isOnTheFly).forEach(holder::registerProblem)
            }
          }

        if (element == file) {
          checkTextLevel(file, holder)
        }
      }
    }
  }

  private fun checkTextLevel(file: PsiFile, holder: ProblemsHolder) {
    TreeRuleChecker.checkTextLevelProblems(file).forEach { reportProblem(it, holder) }
  }

  private fun reportProblem(problem: TextProblem, holder: ProblemsHolder) {
    CheckerRunner(problem.text).toProblemDescriptors(problem, holder.isOnTheFly)
      .forEach { holder.registerProblem(it) }
  }

  private fun hasTooLowSeverity(session: LocalInspectionToolSession): Boolean {
    return inspections.all { InspectionProfileManager.hasTooLowSeverity(session, it) }
  }

  private fun areDisabled(session: LocalInspectionToolSession): Boolean {
    val project = session.file.project
    val profile = InspectionProfileManager.getInstance(project).currentProfile
    return inspections.all { inspection ->
      val tools = profile.getToolsOrNull(inspection.shortName, project)
      tools == null || !tools.isEnabled
    }
  }

  /**
   * Most of those methods are used in Grazie Pro.
   */
  @Suppress("CompanionObjectInExtension")
  companion object {
    private val inspections: List<LocalInspectionTool> = listOf(Grammar(), Style())

    private const val MAX_TEXT_LENGTH_IN_PSI_ELEMENT: Int = 50_000
    private const val MAX_TEXT_LENGTH_IN_FILE = 200_000
    const val GRAMMAR_INSPECTION: String = "GrazieInspection"
    const val STYLE_INSPECTION: String = "GrazieStyle"

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
    fun skipCheckingTooLargeTexts(texts: List<TextContent>): Boolean {
      if (texts.isEmpty()) return false
      if (texts.sumOf { it.length } > MAX_TEXT_LENGTH_IN_PSI_ELEMENT) return true

      val file = texts[0].containingFile
      if (file.textLength <= MAX_TEXT_LENGTH_IN_FILE) return false

      return CachedValuesManager.getCachedValue(file) {
        val checkedDomains = checkedDomains()
        val contents = findAllTextContents(file.viewProvider, TextContent.TextDomain.ALL)
        logger<GrazieInspection>().debug("Evaluating text length of: ${TextContentRelatedData(file, contents)}")
        val length = contents.asSequence().filter { it.domain in checkedDomains }.sumOf { it.length }
        CachedValueProvider.Result.create(length > MAX_TEXT_LENGTH_IN_FILE, service<GrazieConfig>(), file)
      }
    }

    @JvmStatic
    fun ignoreGrammarChecking(file: PsiFile): Boolean = hasSpellChecking && isSpellCheckingDisabled(file)

    // those who disable spell-checking probably don't want grammar checks either
    private fun isSpellCheckingDisabled(file: PsiFile) = SpellCheckingEditorCustomization.isSpellCheckingDisabled(file)

    @JvmStatic
    fun checkedDomains(): Set<TextContent.TextDomain> {
      val config = GrazieConfig.get()
      val result = EnumSet.of(TextContent.TextDomain.PLAIN_TEXT)
      if (config.checkingContext.isCheckInStringLiteralsEnabled) {
        result.add(TextContent.TextDomain.LITERALS)
      }
      if (config.checkingContext.isCheckInCommentsEnabled) {
        result.add(TextContent.TextDomain.COMMENTS)
      }
      if (config.checkingContext.isCheckInDocumentationEnabled) {
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

    data class TextContentRelatedData(private val psiFile: PsiFile, val contents: Set<TextContent>) {
      override fun toString(): String {
        return "[fileType = ${psiFile.viewProvider.virtualFile.fileType}, " +
               "fileLanguage = ${psiFile.language}, " +
               "viewProviderLanguages = ${psiFile.viewProvider.allFiles.map { it.language }.toSet()}, " +
               "parentLanguages = ${contents.map { it.commonParent }.map { it.language }.toSet()}," +
               "isPhysical = ${psiFile.isPhysical}, " +
               "contentLengths = ${contents.map { it.length }}]"
      }
    }
  }
}
