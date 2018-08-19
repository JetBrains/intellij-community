// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilBase

class ShowDecompiledClassAction : AnAction(IdeaDecompilerBundle.message("action.show.decompiled.name")) {
  override fun update(e: AnActionEvent) {
    val psiElement = getPsiElement(e)
    val visible = psiElement?.containingFile is PsiClassOwner
    e.presentation.isVisible = visible
    e.presentation.isEnabled = visible && getOriginalFile(psiElement) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project != null) {
      val file = getOriginalFile(getPsiElement(e))
      if (file != null) {
        PsiNavigationSupport.getInstance().createNavigatable(project, file, -1).navigate(true)
      }
    }
  }

  private fun getPsiElement(e: AnActionEvent): PsiElement? {
    val project = e.project ?: return null
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return e.getData(CommonDataKeys.PSI_ELEMENT)
    return PsiUtilBase.getPsiFileInEditor(editor, project)?.findElementAt(editor.caretModel.offset)
  }

  private fun getOriginalFile(psiElement: PsiElement?): VirtualFile? {
    val psiClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass::class.java, false)
    val file = psiClass?.originalElement?.containingFile?.virtualFile
    return if (file != null && file.fileType == JavaClassFileType.INSTANCE) file else null
  }
}