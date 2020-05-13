package com.intellij.grazie.ide.inspection.grammar.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grammar.Typo
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import javax.swing.Icon

class GrazieDisableRuleQuickFix(val typo: Typo) : LocalQuickFix, Iconable {
  private val rule = typo.info.rule

  override fun getIcon(flags: Int): Icon = AllIcons.Actions.Cancel

  override fun getName() = msg("grazie.grammar.quickfix.suppress.rule.text", typo.info.shortMessage)

  override fun getFamilyName(): String = msg("grazie.grammar.quickfix.suppress.rule.family")

  override fun startInWriteAction() = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = typo.location.element ?: return
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