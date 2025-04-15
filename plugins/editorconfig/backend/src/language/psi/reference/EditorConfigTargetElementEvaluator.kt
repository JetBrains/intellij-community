// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.reference

import com.intellij.codeInsight.TargetElementEvaluator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

class EditorConfigTargetElementEvaluator : TargetElementEvaluator {
  override fun includeSelfInGotoImplementation(element: PsiElement): Boolean = false
  override fun getElementByReference(ref: PsiReference, flags: Int): PsiElement? = ref.resolve()
}
