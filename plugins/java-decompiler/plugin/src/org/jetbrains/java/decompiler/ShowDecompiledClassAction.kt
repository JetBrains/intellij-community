/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.decompiler

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
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
        OpenFileDescriptor(project, file, -1).navigate(true)
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