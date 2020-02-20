package com.intellij.grazie.ide.inspection.grammar.problem.suppress

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grammar.Typo
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

class GrazieDisableCategoryIntention(typo: Typo) : GrazieDisableIntention() {
  private val lang = typo.info.lang
  private val category = typo.info.rule.category

  override fun getText() = msg("grazie.grammar.quickfix.suppress.category.text", category.getName(lang.jLanguage))

  override fun getFamilyName() = msg("grazie.grammar.quickfix.suppress.category.family")

  override fun invoke(project: Project, editor: Editor, element: PsiElement) {
    val action = object : BasicUndoableAction(element.containingFile?.virtualFile) {
      private val toDisable = LangTool.getTool(lang).allActiveRules.filter {
        it.category == category
      }.distinctBy { it.id }

      override fun redo() {
        GrazieConfig.update { state ->
          state.copy(
            userEnabledRules = state.userEnabledRules - toDisable.map { it.id },
            userDisabledRules = state.userDisabledRules + toDisable.map { it.id }
          )
        }
      }

      override fun undo() {
        GrazieConfig.update { state ->
          state.copy(
            userEnabledRules = state.userEnabledRules + toDisable.map { it.id },
            userDisabledRules = state.userDisabledRules - toDisable.map { it.id }
          )
        }
      }
    }

    action.redo()
    UndoManager.getInstance(project).undoableActionPerformed(action)
  }

}