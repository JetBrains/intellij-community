package com.intellij.grazie.ide.inspection.detection.quickfix

import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.utils.englishName
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.fus.GrazieFUSCounter
import com.intellij.grazie.utils.PsiPointer
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import javax.swing.Icon

class NeverSuggestLanguageQuickFix(private val file: PsiPointer<PsiFile>, private val languages: Set<Language>) : LocalQuickFix, Iconable {
  override fun getIcon(flags: Int): Icon = AllIcons.Actions.Cancel

  override fun getFamilyName(): String = GrazieBundle.message("grazie.detection.quickfix.disable.family")

  override fun getName() = when (languages.size) {
    in 1..3 -> GrazieBundle.message("grazie.detection.quickfix.disable.several.text", languages.joinToString { it.englishName })
    else -> GrazieBundle.message("grazie.detection.quickfix.disable.many.text")
  }

  override fun startInWriteAction() = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val file = file.virtualFile ?: return

    val action = object : BasicUndoableAction(file) {
      override fun redo() {
        GrazieConfig.update { state -> state.copy(detectionContext = state.detectionContext.disable(languages)) }
        GrazieFUSCounter.languagesSuggested(languages, isEnabled = false)
      }

      override fun undo() {
        GrazieConfig.update { state -> state.copy(detectionContext = state.detectionContext.enable(languages)) }
      }
    }

    action.redo()
    UndoManager.getInstance(project).undoableActionPerformed(action)
  }
}
