// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiNamedElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FileDumbRenameHandler : RenameHandler, DumbAware {
  private fun getElement(dataContext: DataContext): PsiElement? {
    if (dataContext.getData(CommonDataKeys.EDITOR) != null) return null

    val element = PsiElementRenameHandler.getElement(dataContext)
    return when {
      element == null -> null
      element is PsiFileSystemItem -> element
      element !is PsiNamedElement -> null

      /**
      Check if the element replaces a file in the project view (i.e., public java class or single kotlin class)
      Some nodes in the project view are not files and not directories.
      For example, a Java file with a single class (e.g. `Foo` in `Foo.java`)
      When selecting such a node in the project view, we want to rename the file it belongs to.
       */
      with(element.parent) {
        this is PsiFile && this == this.containingFile && this.name.substringBeforeLast(".") == element.name
      } -> element.parent

      else -> null
    }
  }

  override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
    if (!Registry.`is`("rename.files.in.dumb.mode.enable")) return false

    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return false
    if (!DumbService.isDumb(project)) return false
    return getElement(dataContext) != null
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    val element = getElement(dataContext)
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE)
    val nameSuggestionContext = file.findElementAt(editor.getCaretModel().offset)

    // this call should use com.intellij.refactoring.rename.RenamePsiFileDumbProcessor
    PsiElementRenameHandler.rename(element!!, project, nameSuggestionContext, editor)
  }

  override fun invoke(project: Project, elements: Array<PsiElement?>, dataContext: DataContext) {
    val element = getElement(dataContext)
    val editor = CommonDataKeys.EDITOR.getData(dataContext)

    // this call should use com.intellij.refactoring.rename.RenamePsiFileDumbProcessor
    PsiElementRenameHandler.rename(element!!, project, element, editor)
  }
}
