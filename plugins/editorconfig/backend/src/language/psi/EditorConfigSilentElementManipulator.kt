// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.util.IncorrectOperationException

class EditorConfigSilentElementManipulator : ElementManipulator<PsiElement> {
  override fun handleContentChange(element: PsiElement, range: TextRange, newContent: String): PsiElement {
    if (element !is PsiNamedElement) throw IncorrectOperationException()
    return element.setName(newContent)
  }

  override fun handleContentChange(element: PsiElement, newContent: String): PsiElement =
    handleContentChange(element, getRangeInElement(element), newContent)

  override fun getRangeInElement(element: PsiElement): TextRange =
    TextRange(0, element.textLength)
}
