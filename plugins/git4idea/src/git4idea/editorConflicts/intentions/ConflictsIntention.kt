// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.editorConflicts.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager

abstract class ConflictsIntention(element: PsiElement, val intentionText: String) : IntentionAction {
  private val markerPtr = SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)

  override fun getText() = intentionText
  override fun getFamilyName() = "Conflict Actions"
  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    if (editor == null || file == null) return
    val d = editor.document
    val marker = markerPtr.element ?: return
    doInvoke(project, editor, d, marker)
  }

  override fun startInWriteAction() = true

  protected abstract fun doInvoke(project: Project, editor: Editor, d: Document, marker: PsiElement)
}