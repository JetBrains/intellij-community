package org.jetbrains.intellij.plugins.journey.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import org.jetbrains.intellij.plugins.journey.JourneyBundle
import org.jetbrains.intellij.plugins.journey.diagram.JourneyShowDiagram
import org.jetbrains.intellij.plugins.journey.util.JourneyNavigationUtil

class StartJourneyIntentionAction: IntentionAction, Iconable, LowPriorityAction {
  override fun startInWriteAction() = false

  override fun getFamilyName() = text

  override fun getText() = JourneyBundle.message("intention.name.start.journey")

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    return true
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    JourneyShowDiagram().showDiagram(requireNotNull(JourneyNavigationUtil.getPsiElement(editor, file)))
  }

  override fun getIcon(flags: Int) = AllIcons.FileTypes.Diagram
}