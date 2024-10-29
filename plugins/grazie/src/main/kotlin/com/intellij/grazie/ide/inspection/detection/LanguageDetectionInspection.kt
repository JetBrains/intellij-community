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
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.KeyWithDefaultValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace

internal class LanguageDetectionInspection : LocalInspectionTool() {

  private val key = KeyWithDefaultValue.create("language-detection-inspection-key", DetectionContext.Local())

  override fun inspectionStarted(session: LocalInspectionToolSession, isOnTheFly: Boolean) {
    session.getUserData(key)!!.clear()
  }

  override fun inspectionFinished(session: LocalInspectionToolSession, holder: ProblemsHolder) {
    val state = GrazieConfig.get()
    val context = session.getUserData(key)!!
    val languages = context.getToNotify((state.detectionContext.disabled + state.availableLanguages.map { it.toLanguage() }).toSet())

    if (languages.isEmpty()) {
      return
    }
    val descriptor = runReadAction {
      LanguageDetectionProblemDescriptor.create(holder.manager, holder.isOnTheFly, session.file, languages)
    }
    if (descriptor != null) {
      holder.registerProblem(descriptor)
    }
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val file = holder.file
    if (!isOnTheFly || InjectedLanguageManager.getInstance(holder.project).isInjectedFragment(
        file) || GrazieInspection.ignoreGrammarChecking(file))
      return PsiElementVisitor.EMPTY_VISITOR

    val domains = GrazieInspection.checkedDomains()
    val areChecksDisabled = GrazieInspection.getDisabledChecker(file)
    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element is PsiWhiteSpace || areChecksDisabled(element)) return
        val texts = TextExtractor.findUniqueTextsAt(element, domains)
        if (GrazieInspection.skipCheckingTooLargeTexts(texts)) return

        val context = session.getUserData(key)!!
        texts.forEach {
          ProgressManager.checkCanceled()
          LangDetector.updateContext(it, context)
        }
      }
    }
  }

  override fun getDisplayName() = GrazieBundle.message("grazie.detection.inspection.text")
}
