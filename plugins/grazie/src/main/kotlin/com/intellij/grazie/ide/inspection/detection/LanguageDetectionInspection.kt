package com.intellij.grazie.ide.inspection.detection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.DetectionContext
import com.intellij.grazie.detection.BatchLangDetector
import com.intellij.grazie.detection.toLanguage
import com.intellij.grazie.ide.inspection.detection.problem.LanguageDetectionProblemDescriptor
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

internal class LanguageDetectionInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val file = holder.file
    if (!isOnTheFly || InjectedLanguageManager.getInstance(holder.project).isInjectedFragment(file)
        || GrazieInspection.ignoreGrammarChecking(file)
        || InspectionProfileManager.hasTooLowSeverity(session, this)) {
      return PsiElementVisitor.EMPTY_VISITOR
    }

    val areChecksDisabled = GrazieInspection.getDisabledChecker(file)
    return object : PsiElementVisitor() {
      override fun visitFile(psiFile: PsiFile) {
        if (areChecksDisabled(file)) return
        val context = DetectionContext.Local()
        BatchLangDetector.updateContext(file, context)

        val state = GrazieConfig.get()
        val languages = context.getToNotify((state.detectionContext.disabled + state.availableLanguages.map { it.toLanguage() }).toSet())
        LanguageDetectionProblemDescriptor.create(holder.manager, holder.isOnTheFly, session.file, languages)
          ?.let { holder.registerProblem(it) }
      }
    }
  }

  override fun getDisplayName() = GrazieBundle.message("grazie.detection.inspection.text")
}
