// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.todo.backend.model

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.util.PsiTreeUtil

internal class TodoBackendPsiListener(
  private val scheduleFileChanges: (List<VirtualFile>) -> Unit
) : PsiTreeChangeAdapter() {

  override fun childAdded(event: PsiTreeChangeEvent) = scheduleFor(event)
  override fun childRemoved(event: PsiTreeChangeEvent) = scheduleFor(event)
  override fun childReplaced(event: PsiTreeChangeEvent) = scheduleFor(event)
  override fun childMoved(event: PsiTreeChangeEvent) = scheduleFor(event)
  override fun childrenChanged(event: PsiTreeChangeEvent) = scheduleFor(event)

  private fun scheduleFor(event: PsiTreeChangeEvent) {
    val file = affectedFile(event) ?: return
    scheduleFileChanges(listOf(file))
  }

  private fun affectedFile(event: PsiTreeChangeEvent): VirtualFile? {
    event.file?.virtualFile?.let { return it }
    val child: PsiElement? = event.child
    if (child is PsiFile) return child.virtualFile
    if (child != null && PsiTreeUtil.getParentOfType(child, PsiComment::class.java, false) != null) {
      return child.containingFile?.virtualFile
    }
    return null
  }
}