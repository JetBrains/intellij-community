package com.intellij.grazie.ide.inspection.detection.problem.suppress

import com.intellij.codeInspection.SuppressIntentionAction
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.detection.displayName
import com.intellij.grazie.ide.fus.GrazieFUSCounter
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import tanvd.grazie.langdetect.model.Language
import javax.swing.Icon

class DisableLanguageSuggestionIntention(private val languages: Set<Language>) : SuppressIntentionAction(), Iconable {
  override fun getIcon(flags: Int): Icon = AllIcons.Actions.Cancel

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement) = true

  override fun getText() = when (languages.size) {
    in 1..3 -> GrazieBundle.message("grazie.detection.intention.disable.several.text", languages.joinToString { it.displayName })
    else -> GrazieBundle.message("grazie.detection.intention.disable.many.text")
  }

  override fun getFamilyName(): String = GrazieBundle.message("grazie.detection.intention.disable.family")

  override fun startInWriteAction() = false

  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    val action = object : BasicUndoableAction(element.containingFile?.virtualFile) {
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
