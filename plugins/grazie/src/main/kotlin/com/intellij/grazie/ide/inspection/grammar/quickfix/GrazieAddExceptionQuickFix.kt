package com.intellij.grazie.ide.inspection.grammar.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.IntentionAndQuickFixAction
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.config.SuppressingContext
import com.intellij.grazie.ide.ui.components.dsl.msg
import com.intellij.grazie.text.SuppressionPattern
import com.intellij.icons.AllIcons
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiFileRange
import javax.swing.Icon

open class GrazieAddExceptionQuickFix(
  private val suppressionPattern: SuppressionPattern, private val underlineRanges: List<SmartPsiFileRange>
) : IntentionAndQuickFixAction(), Iconable, Comparable<IntentionAction>, DumbAware {

  @Suppress("unused") // used in Grazie Professional
  constructor(suppressionPattern: SuppressionPattern, underlineRange: SmartPsiFileRange) : this(suppressionPattern, listOf(underlineRange))

  override fun getIcon(flags: Int): Icon = AllIcons.Actions.AddToDictionary

  override fun getFamilyName(): String = msg("grazie.grammar.quickfix.ignore.family")

  override fun getName(): String {
    val errorText = StringUtil.shortenTextWithEllipsis(suppressionPattern.errorText, 50, 20)
    return when (suppressionPattern.sentenceText) {
      null -> msg("grazie.grammar.quickfix.ignore.text.no.context", errorText)
      else -> msg("grazie.grammar.quickfix.ignore.text.with.context", errorText)
    }
  }

  override fun compareTo(other: IntentionAction): Int {
    return if (other is GrazieRuleSettingsAction) -1 else 0
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

    underlineRanges.forEach { underline ->
      underline.range?.let { GrazieReplaceTypoQuickFix.removeHighlightersWithExactRange(file.viewProvider.document, project, it) }
    }

    UndoManager.getInstance(project).undoableActionPerformed(action)
  }
}
