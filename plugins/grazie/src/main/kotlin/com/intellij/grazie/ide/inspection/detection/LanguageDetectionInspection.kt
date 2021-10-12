package com.intellij.grazie.ide.inspection.detection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.DetectionContext
import com.intellij.grazie.detection.LangDetector
import com.intellij.grazie.detection.toLanguage
import com.intellij.grazie.ide.inspection.detection.problem.LanguageDetectionProblemDescriptor
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.text.TextExtractor
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

internal class LanguageDetectionInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val file = holder.file
    if (!isOnTheFly || InjectedLanguageManager.getInstance(holder.project).isInjectedFragment(file) || GrazieInspection.ignoreGrammarChecking(file))
      return PsiElementVisitor.EMPTY_VISITOR

    val domains = GrazieInspection.checkedDomains()
    val fileLanguage = file.language
    val areChecksDisabled = GrazieInspection.getDisabledChecker(fileLanguage)
    val context = DetectionContext.Local()
    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (areChecksDisabled(element)) return
        TextExtractor.findTextsAt(element, domains).forEach { LangDetector.updateContext(it, context) }
      }

      override fun visitFile(file: PsiFile) {
        super.visitFile(file)
        val state = GrazieConfig.get()
        val languages = context.getToNotify((state.detectionContext.disabled + state.availableLanguages.map { it.toLanguage() }).toSet())
        if (languages.isNotEmpty()) {
          holder.registerProblem(LanguageDetectionProblemDescriptor.create(holder.manager, holder.isOnTheFly, file, languages))
        }
      }
    }
  }

  override fun getDisplayName() = GrazieBundle.message("grazie.detection.inspection.text")
}
