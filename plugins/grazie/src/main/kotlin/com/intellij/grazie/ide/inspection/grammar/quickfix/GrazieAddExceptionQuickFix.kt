package com.intellij.grazie.ide.inspection.grammar.quickfix

import com.intellij.codeInspection.IntentionAndQuickFixAction
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.SuppressingContext
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.text.SuppressionPattern
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiFileRange
import javax.swing.Icon

internal class GrazieAddExceptionQuickFix(
  private val suppressionPattern: SuppressionPattern, private val underlineRange: SmartPsiFileRange
) : IntentionAndQuickFixAction(), Iconable {

  override fun getIcon(flags: Int): Icon = AllIcons.Actions.AddToDictionary

  override fun getFamilyName(): String = msg("grazie.grammar.quickfix.suppress.sentence.family")

  override fun getName(): String {
    return msg("grazie.grammar.quickfix.suppress.sentence.text", suppressionPattern.errorText)
  }

  override fun startInWriteAction() = false

  override fun applyFix(project: Project, file: PsiFile, editor: Editor?) {
    val action = object : BasicUndoableAction(file.virtualFile) {
      override fun redo() {
        GrazieConfig.update { state ->
          state.copy(
            suppressingContext = SuppressingContext(state.suppressingContext.suppressed + suppressionPattern.full)
          )
        }
      }

      override fun undo() {
        GrazieConfig.update { state ->
          state.copy(
            suppressingContext = SuppressingContext(state.suppressingContext.suppressed - suppressionPattern.full)
          )
        }
      }
    }

    action.redo()

    underlineRange.range?.let { file.viewProvider.document?.invalidateHighlighter(project, TextRange.create(it)) }

    UndoManager.getInstance(project).undoableActionPerformed(action)
  }
}

internal fun Document.invalidateHighlighter(project: Project, range: TextRange) {
  val model = DocumentMarkupModel.forDocument(this, project, false)
  model?.allHighlighters?.filter { TextRange.create(it) == range }.orEmpty().forEach { model.removeHighlighter(it) }
}
