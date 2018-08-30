// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.psi.reference

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement

class EditorConfigConstantReference(element: EditorConfigDescribableElement) : PsiReferenceBase<EditorConfigDescribableElement>(element) {
  override fun resolve(): PsiElement? = null
}
