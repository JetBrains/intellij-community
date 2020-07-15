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
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.lazyConfig
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.KeyWithDefaultValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

internal class LanguageDetectionInspection : LocalInspectionTool() {
  companion object : GrazieStateLifecycle {
    private val key = KeyWithDefaultValue.create("language-detection-inspection-key", DetectionContext.Local())

    private var enabledStrategiesIDs: Set<String> by lazyConfig(this::init)
    private var disabledStrategiesIDs: Set<String> by lazyConfig(this::init)

    private var available: Set<Lang> by lazyConfig(this::init)
    private var disabled: DetectionContext.State by lazyConfig(this::init)

    override fun init(state: GrazieConfig.State) {
      available = state.availableLanguages
      disabled = state.detectionContext
      enabledStrategiesIDs = state.enabledGrammarStrategies
      disabledStrategiesIDs = state.disabledGrammarStrategies
    }

    override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
      if (
        prevState.enabledGrammarStrategies != newState.enabledGrammarStrategies
        || prevState.disabledGrammarStrategies != newState.disabledGrammarStrategies
        || prevState.availableLanguages != newState.availableLanguages
        || prevState.detectionContext != newState.detectionContext
      ) {
        init(newState)
      }
    }
  }

  override fun inspectionStarted(session: LocalInspectionToolSession, isOnTheFly: Boolean) {
    session.getUserData(key)!!.clear()
  }

  override fun inspectionFinished(session: LocalInspectionToolSession, holder: ProblemsHolder) {
    val context = session.getUserData(key)!!
    val languages = context.getToNotify((disabled.disabled + available.map { it.toLanguage() }).toSet())

    if (languages.isEmpty()) return

    holder.registerProblem(LanguageDetectionProblemDescriptor.create(holder.manager, holder.isOnTheFly, session.file, languages))
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!isOnTheFly) return PsiElementVisitor.EMPTY_VISITOR

    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (InjectedLanguageManager.getInstance(holder.project).isInjectedFragment(holder.file)) return

        val strategies = LanguageGrammarChecking.getStrategiesForElement(element, enabledStrategiesIDs, disabledStrategiesIDs)

        for (strategy in strategies) {
          val (_, _, text) = GraziePsiElementProcessor.processElements(element, strategy)
          LangDetector.updateContext(text, session.getUserData(key)!!)
          break
        }
      }
    }
  }

  override fun getDisplayName() = GrazieBundle.message("grazie.detection.inspection.text")
}