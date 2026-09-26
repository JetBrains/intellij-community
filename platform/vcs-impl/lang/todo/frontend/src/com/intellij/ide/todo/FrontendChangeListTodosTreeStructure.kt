// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FrontendChangeListTodosTreeStructure(project: Project) : TodoTreeStructure(project) {

  override fun accept(psiFile: PsiFile): Boolean {
    if (!psiFile.isValid) return false

    val file = psiFile.virtualFile
    val builder = myBuilder as? FrontendChangeListTodosTreeBuilder
    return file != null &&
           builder != null &&
           builder.isChangedFile(file) &&
           acceptTodoFilter(psiFile)
  }
}