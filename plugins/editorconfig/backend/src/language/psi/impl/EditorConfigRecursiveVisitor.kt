// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.impl

import com.intellij.editorconfig.common.syntax.psi.EditorConfigVisitor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveVisitor

abstract class EditorConfigRecursiveVisitor : EditorConfigVisitor(), PsiRecursiveVisitor {
  override fun visitElement(element: PsiElement) {
    ProgressManager.checkCanceled()
    element.acceptChildren(this)
  }
}
