// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.todo.backend.model

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.util.PsiTreeUtil

internal class TodoBackendPsiListener(
  private val scheduleInitialScan: () -> Unit,
  private val scheduleFileChanges: (VirtualFile) -> Unit
) : PsiTreeChangeAdapter() {

  override fun childAdded(event: PsiTreeChangeEvent) = scheduleFor(event)
  override fun childRemoved(event: PsiTreeChangeEvent) = scheduleFor(event)
  override fun childReplaced(event: PsiTreeChangeEvent) = scheduleFor(event)
  override fun childMoved(event: PsiTreeChangeEvent) = scheduleFor(event)
  override fun childrenChanged(event: PsiTreeChangeEvent) = scheduleFor(event)

  override fun propertyChanged(event: PsiTreeChangeEvent) {
    when (event.propertyName) {
      PsiTreeChangeEvent.PROP_FILE_NAME, PsiTreeChangeEvent.PROP_WRITABLE -> scheduleFor(event)
      PsiTreeChangeEvent.PROP_DIRECTORY_NAME,
      PsiTreeChangeEvent.PROP_UNLOADED_PSI,
      PsiTreeChangeEvent.PROP_ROOTS,
      PsiTreeChangeEvent.PROP_FILE_TYPES -> scheduleInitialScan()
    }
  }

  private fun scheduleFor(event: PsiTreeChangeEvent) {
    val file = affectedFile(event)
    if (file != null) {
      scheduleFileChanges(file)
    }
    else if (event.child is PsiDirectory || event.newChild is PsiDirectory) {
      scheduleInitialScan()
    }
  }

  private fun affectedFile(event: PsiTreeChangeEvent): VirtualFile? {
    event.file?.virtualFile?.let { return it }
    val child: PsiElement? = event.child ?: event.newChild ?: event.element
    if (child is PsiFile) return child.virtualFile
    if (child != null && PsiTreeUtil.getParentOfType(child, PsiComment::class.java, false) != null) {
      return child.containingFile?.virtualFile
    }
    return null
  }
}