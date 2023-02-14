package com.intellij.cce.actions

import com.intellij.cce.EvaluationPluginBundle
import com.intellij.cce.dialog.EvaluateHereSettingsDialog
import com.intellij.cce.evaluation.BackgroundStepFactory
import com.intellij.cce.evaluation.EvaluationProcess
import com.intellij.cce.evaluation.EvaluationRootInfo
import com.intellij.cce.util.FilesHelper
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement

class EvaluateCompletionHereAction : AnAction() {
  private companion object {
    val LOG = Logger.getInstance(EvaluateCompletionHereAction::class.java)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return LOG.error("Project is null.")
    val caret = e.getData(CommonDataKeys.CARET) ?: return LOG.error("No value for key ${CommonDataKeys.CARET}.")
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return LOG.error("No value for key ${CommonDataKeys.EDITOR}.")
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return LOG.error("No value for key ${CommonDataKeys.VIRTUAL_FILE}.")
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return LOG.error("No value for key ${CommonDataKeys.PSI_FILE}.")
    val psi = psiFile.findElementAt(caret.offset) ?: return LOG.error("No psi element under caret.")
    val language = FilesHelper.getLanguageByExtension(file.extension ?: "")
    if (language == null) {
      Messages.showInfoMessage(project,
                               EvaluationPluginBundle.message("EvaluateCompletionHereAction.error.text"),
                               EvaluationPluginBundle.message("EvaluateCompletionHereAction.error.title"))
      return
    }

    val settingsDialog = EvaluateHereSettingsDialog(project, language.displayName, file.path)
    val result = settingsDialog.showAndGet()
    if (!result) return
    val config = settingsDialog.buildConfig()
    val workspace = EvaluationWorkspace.create(config)
    val parentPsiElement = getParentOnSameLine(psi, caret.offset, editor)
    val process = EvaluationProcess.build({
                                            shouldGenerateActions = true
                                            shouldInterpretActions = true
                                            shouldHighlightInIde = true
                                          }, BackgroundStepFactory(config, project, false, null,
                                                                   EvaluationRootInfo(false, caret.offset, parentPsiElement)))
    process.startAsync(workspace)
  }

  private fun getParentOnSameLine(element: PsiElement, offset: Int, editor: Editor): PsiElement {
    val line = editor.offsetToLogicalPosition(offset).line
    var curElement = element
    var parent = element
    while (editor.offsetToLogicalPosition(curElement.textOffset).line == line) {
      parent = curElement
      curElement = curElement.parent
    }
    return parent
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    if (e.project != null) {
      presentation.isEnabled = e.getData(CommonDataKeys.CARET) != null
      presentation.isVisible = e.place == ActionPlaces.ACTION_SEARCH || !ApplicationInfo.getInstance().build.isSnapshot
    }
    else {
      presentation.isEnabledAndVisible = false
    }
  }
}