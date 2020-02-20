package com.intellij.grazie.ide.inspection.grammar.problem.suppress

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grammar.Typo
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

class GrazieDisableContextIntention(val typo: Typo) : GrazieDisableIntention() {
  override fun getText() = msg("grazie.grammar.quickfix.suppress.sentence.text", typo.info.shortMessage)

  override fun getFamilyName(): String = msg("grazie.grammar.quickfix.suppress.sentence.family")

  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    val action = object : BasicUndoableAction(element.containingFile?.virtualFile) {
      override fun redo() {
        GrazieConfig.update { state ->
          state.copy(
            suppressionContext = state.suppressionContext.suppress(typo)
          )
        }
      }

      override fun undo() {
        GrazieConfig.update { state ->
          state.copy(
            suppressionContext = state.suppressionContext.unsuppress(typo)
          )
        }
      }
    }

    action.redo()
    UndoManager.getInstance(project).undoableActionPerformed(action)
  }

}