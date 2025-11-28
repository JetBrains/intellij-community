// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FileDumbRenameHandler : RenameHandler, DumbAware {
  private fun getElement(dataContext: DataContext): PsiElement? {
    return PsiElementRenameHandler.getElement(dataContext) as? PsiFileSystemItem
  }

  override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return false
    if (!DumbService.isDumb(project)) return false
    return getElement(dataContext) != null
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    val element: PsiElement? = getElement(dataContext)
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE)
    val nameSuggestionContext = file.findElementAt(editor.getCaretModel().offset)

    // this call should use com.intellij.refactoring.rename.RenamePsiFileDumbProcessor
    PsiElementRenameHandler.rename(element!!, project, nameSuggestionContext, editor )
  }

  override fun invoke(project: Project, elements: Array<PsiElement?>, dataContext: DataContext) {
    val element = getElement(dataContext)
    val editor = CommonDataKeys.EDITOR.getData(dataContext)

    // this call should use com.intellij.refactoring.rename.RenamePsiFileDumbProcessor
    PsiElementRenameHandler.rename(element!!, project, element, editor)
  }
}
