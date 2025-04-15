package org.editorconfig.language.psi

import com.intellij.psi.PsiReference
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface EditorConfigReferenceSupport {

  fun getReference(element: EditorConfigHeader): PsiReference

  fun getReference(element: EditorConfigDescribableElement) : PsiReference?
}
