package com.intellij.grazie.ide.inspection.detection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.DetectionContext
import com.intellij.grazie.detection.LangDetector
import com.intellij.grazie.detection.toLanguage
import com.intellij.grazie.grammar.ide.GraziePsiElementProcessor
import com.intellij.grazie.ide.inspection.detection.problem.LanguageDetectionProblemDescriptor
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.KeyWithDefaultValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

internal class LanguageDetectionInspection : LocalInspectionTool() {
  companion object {
    private val key = KeyWithDefaultValue.create("language-detection-inspection-key", DetectionContext.Local())
  }

  override fun inspectionStarted(session: LocalInspectionToolSession, isOnTheFly: Boolean) {
    session.getUserData(key)!!.clear()
  }

  override fun inspectionFinished(session: LocalInspectionToolSession, holder: ProblemsHolder) {
    val state = GrazieConfig.get()
    val context = session.getUserData(key)!!
    val languages = context.getToNotify((state.detectionContext.disabled + state.availableLanguages.map { it.toLanguage() }).toSet())

    if (languages.isEmpty()) return

    holder.registerProblem(LanguageDetectionProblemDescriptor.create(holder.manager, holder.isOnTheFly, session.file, languages))
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!isOnTheFly || InjectedLanguageManager.getInstance(holder.project).isInjectedFragment(holder.file))
      return PsiElementVisitor.EMPTY_VISITOR

    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        val strategies = LanguageGrammarChecking.getEnabledStrategiesForElement(element)

        for (strategy in strategies) {
          val (_, _, _, text) = GraziePsiElementProcessor.processElements(listOf(element), strategy)
          LangDetector.updateContext(text.first().text, session.getUserData(key)!!)
          break
        }
      }
    }
  }

  override fun getDisplayName() = GrazieBundle.message("grazie.detection.inspection.text")
}
