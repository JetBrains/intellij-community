// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope

private object LockReqPsiOpsProvider : LanguageExtension<LockReqPsiOps>("DevKit.lang.LockReqPsiOps")

interface LockReqPsiOps {
  companion object {
    fun forLanguage(language: Language): LockReqPsiOps = LockReqPsiOpsProvider.forLanguage(language)
  }

  fun getMethodCallees(method: PsiMethod): List<PsiMethod>
  fun findInheritors(method: PsiMethod, scope: GlobalSearchScope, maxImpl: Int, handler: (PsiMethod) -> Unit)
  fun findImplementations(interfaceClass: PsiClass, scope: GlobalSearchScope, maxImpl: Int, handler: (PsiClass) -> Unit)
  fun inheritsFromAny(psiClass: PsiClass, baseClassNames: Collection<String>): Boolean
  fun isInPackages(className: String, packagePrefixes: Collection<String>): Boolean
  fun resolveReturnType(method: PsiMethod): PsiClass?
  fun extractTypeArguments(type: PsiType): List<PsiType>
}