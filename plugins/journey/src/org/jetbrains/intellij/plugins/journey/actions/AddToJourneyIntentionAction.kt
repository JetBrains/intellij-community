package org.jetbrains.intellij.plugins.journey.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import org.jetbrains.intellij.plugins.journey.JourneyBundle
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel.getCurrentModel
import org.jetbrains.intellij.plugins.journey.diagram.JourneyShowDiagram.getUmlVirtualFile
import org.jetbrains.intellij.plugins.journey.util.JourneyNavigationUtil

class AddToJourneyIntentionAction: IntentionAction, Iconable, LowPriorityAction {
  override fun startInWriteAction() = false

  override fun getFamilyName() = text

  override fun getText() = JourneyBundle.message("intention.name.add.journey")

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    return true
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    getCurrentModel().addElementWithLayout(requireNotNull(JourneyNavigationUtil.getPsiElement(editor, file)))
    if (editor != null) {
      FileEditorManagerEx.getInstanceExIfCreated(project)?.openFile(getUmlVirtualFile(), null, FileEditorOpenOptions().withRequestFocus())
    }
  }

  override fun getIcon(flags: Int) = AllIcons.FileTypes.Diagram
}