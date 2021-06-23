package com.intellij.grazie.ide.inspection.grammar.quickfix

import com.intellij.codeInspection.IntentionAndQuickFixAction
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.text.Rule
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import javax.swing.Icon

internal class GrazieDisableRuleQuickFix(private val ruleName: String, private val rule: Rule) : IntentionAndQuickFixAction(), Iconable {

  override fun getIcon(flags: Int): Icon = AllIcons.Actions.Cancel

  override fun getName() = msg("grazie.grammar.quickfix.suppress.rule.text", ruleName)

  override fun getFamilyName(): String = msg("grazie.grammar.quickfix.suppress.rule.family")

  override fun startInWriteAction() = false

  override fun applyFix(project: Project, file: PsiFile?, editor: Editor?) {
    val ruleId = rule.globalId
    val action = object : BasicUndoableAction(file?.virtualFile) {
      override fun redo() {
        GrazieConfig.update { state ->
          state.copy(
            userEnabledRules = state.userEnabledRules - ruleId,
            userDisabledRules = if (rule.isEnabledByDefault) state.userDisabledRules + ruleId else state.userDisabledRules
          )
        }
      }

      override fun undo() {
        GrazieConfig.update { state ->
          state.copy(
            userEnabledRules = if (rule.isEnabledByDefault) state.userEnabledRules else state.userEnabledRules + ruleId,
            userDisabledRules = state.userDisabledRules - ruleId
          )
        }
      }
    }

    action.redo()
    UndoManager.getInstance(project).undoableActionPerformed(action)
  }

}