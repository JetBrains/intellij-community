package com.intellij.grazie.ide.inspection.grammar.problem.suppress

import com.intellij.codeInspection.SuppressIntentionAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import javax.swing.Icon

abstract class GrazieDisableIntention : SuppressIntentionAction(), Iconable {
  override fun getIcon(flags: Int): Icon = AllIcons.Actions.Cancel

  override fun startInWriteAction() = false

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement) = true
}