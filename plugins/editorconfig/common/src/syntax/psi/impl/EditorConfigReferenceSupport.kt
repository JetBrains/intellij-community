package com.intellij.editorconfig.common.syntax.psi.impl

import com.intellij.editorconfig.common.syntax.psi.EditorConfigDescribableElement
import com.intellij.editorconfig.common.syntax.psi.EditorConfigHeader
import com.intellij.psi.PsiReference
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface EditorConfigReferenceSupport {

  fun getReference(element: EditorConfigHeader): PsiReference

  fun getReference(element: EditorConfigDescribableElement): PsiReference?
}
