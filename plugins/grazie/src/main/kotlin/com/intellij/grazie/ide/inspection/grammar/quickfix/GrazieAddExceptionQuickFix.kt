package com.intellij.grazie.ide.inspection.grammar.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.SuppressingContext
import com.intellij.grazie.grammar.Typo
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import javax.swing.Icon

class GrazieAddExceptionQuickFix(val typo: Typo) : LocalQuickFix, Iconable {
  override fun getIcon(flags: Int): Icon = AllIcons.Actions.AddToDictionary

  override fun getFamilyName(): String = msg("grazie.grammar.quickfix.suppress.sentence.family")

  override fun getName() = msg("grazie.grammar.quickfix.suppress.sentence.text", SuppressingContext.preview(typo))

  override fun startInWriteAction() = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = typo.location.element ?: return
    val action = object : BasicUndoableAction(element.containingFile?.virtualFile) {
      override fun redo() {
        GrazieConfig.update { state ->
          state.copy(
            suppressingContext = state.suppressingContext.suppress(typo)
          )
        }
      }

      override fun undo() {
        GrazieConfig.update { state ->
          state.copy(
            suppressingContext = state.suppressingContext.unsuppress(typo)
          )
        }
      }
    }

    action.redo()
    UndoManager.getInstance(project).undoableActionPerformed(action)
  }
}