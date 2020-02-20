package com.intellij.grazie.ide.inspection.grammar.problem.suppress

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grammar.Typo
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

class GrazieDisableRuleIntention(typo: Typo) : GrazieDisableIntention() {
  private val rule = typo.info.rule
  private val shortMessage = typo.info.shortMessage

  override fun getText() = msg("grazie.grammar.quickfix.suppress.rule.text", shortMessage)

  override fun getFamilyName(): String = msg("grazie.grammar.quickfix.suppress.rule.family")

  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    val action = object : BasicUndoableAction(element.containingFile?.virtualFile) {
      override fun redo() {
        GrazieConfig.update { state ->
          state.copy(
            userEnabledRules = state.userEnabledRules - rule.id,
            userDisabledRules = state.userDisabledRules + rule.id
          )
        }
      }

      override fun undo() {
        GrazieConfig.update { state ->
          state.copy(
            userEnabledRules = state.userEnabledRules + rule.id,
            userDisabledRules = state.userDisabledRules - rule.id
          )
        }
      }
    }

    action.redo()
    UndoManager.getInstance(project).undoableActionPerformed(action)
  }

}