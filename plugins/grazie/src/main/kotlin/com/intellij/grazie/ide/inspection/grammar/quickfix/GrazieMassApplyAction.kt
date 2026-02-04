@file:Suppress("IntentionDescriptionNotFoundInspection", "DialogTitleCapitalization")

package com.intellij.grazie.ide.inspection.grammar.quickfix

import com.intellij.codeInsight.intention.CustomizableIntentionAction
import com.intellij.codeInspection.IntentionAndQuickFixAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.icons.GrazieIcons
import com.intellij.grazie.ide.ui.mass.GrazieMassApplyDialog
import com.intellij.grazie.text.ProofreadingProblems
import com.intellij.grazie.text.ProofreadingService
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiFile
import javax.swing.Icon

class GrazieMassApplyAction : IntentionAndQuickFixAction(), Iconable, CustomizableIntentionAction {
  override fun getName(): @IntentionName String = GrazieBundle.message("grazie.mass.apply.action.text")

  override fun getFamilyName(): @IntentionFamilyName String = name

  override fun startInWriteAction(): Boolean = false

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    if (file == null || editor == null) return false
    val range = getSelectionRange(editor)
    if (!range.isEmpty) return ProofreadingService.covers(file, listOf(range))
    val ranges = TextExtractor.findTextAt(file, range.startOffset, TextContent.TextDomain.ALL)?.rangesInFile ?: return false
    return ProofreadingService.covers(file, ranges)
  }

  override fun applyFix(project: Project, file: PsiFile?, editor: Editor?) {
    if (file == null || editor == null) return
    val problems = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      ThrowableComputable {
        ReadAction.compute<ProofreadingProblems, Throwable> {
          ProofreadingService.covering(file, getSelectionRange(editor))
        }
      }, GrazieBundle.message("grazie.mass.apply.action.title"), true, project)
    if (problems.isEmpty) return
    val dialog = GrazieMassApplyDialog(file, problems)
    dialog.show()
    dialog.apply(editor)
  }

  override fun getIcon(flags: Int): Icon = GrazieIcons.Stroke.Grazie

  private fun getSelectionRange(editor: Editor): TextRange {
    return TextRange(editor.getSelectionModel().selectionStart, editor.getSelectionModel().selectionEnd)
  }
}