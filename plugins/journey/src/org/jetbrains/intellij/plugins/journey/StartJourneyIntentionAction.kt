package org.jetbrains.intellij.plugins.journey

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.awt.RelativePoint
import com.intellij.uml.core.actions.ShowDiagram
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramProvider
import org.jetbrains.intellij.plugins.journey.diagram.JourneyNodeIdentity
import java.awt.Point

class StartJourneyIntentionAction: IntentionAction, Iconable, LowPriorityAction {
  override fun startInWriteAction() = false

  override fun getFamilyName() = text

  override fun getText() = JourneyBundle.message("intention.name.start.journey")

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    return true
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    ShowDiagramAction().showDiagram(requireNotNull(guessPsiElement(editor, file)));
  }

  private fun guessPsiElement(editor: Editor?, file: PsiFile?): PsiElement? {
    val offset = editor?.caretModel?.offset ?: return file
    val result = file?.findElementAt(offset) ?: return file
    return result
  }

  private class ShowDiagramAction: ShowDiagram() {
    fun showDiagram(file: PsiElement) {
      val seed = createSeed(file.project, JourneyDiagramProvider(), JourneyNodeIdentity(file), emptyList())
      show(seed, RelativePoint(Point()), null)
    }
  }

  override fun getIcon(flags: Int) = AllIcons.FileTypes.Diagram
}