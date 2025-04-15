package org.editorconfig.language.util

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType

inline fun <reified T : PsiElement> PsiElement.requiredParentOfType(): T {
  return checkNotNull(parentOfType(withSelf = true))
}
