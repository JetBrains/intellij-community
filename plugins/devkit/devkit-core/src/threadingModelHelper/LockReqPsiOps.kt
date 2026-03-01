// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.annotations.RequiresReadLock

private object LockReqPsiOpsProvider : LanguageExtension<LockReqPsiOps>("DevKit.lang.LockReqPsiOps")

/**
 * Language-aware interface that provides extraction for different JVM languages used in application code
 */
interface LockReqPsiOps {
  companion object {
    fun forLanguage(language: Language): LockReqPsiOps = forLanguageOrNull(language) ?: error("No LockReqPsiOps for language $language")

    fun forLanguageOrNull(language: Language): LockReqPsiOps? = LockReqPsiOpsProvider.forLanguage(language)
  }

  @RequiresReadLock
  fun getMethodCallees(method: PsiMethod): List<PsiMethod>

  @RequiresReadLock
  fun findInheritors(method: PsiMethod, scope: GlobalSearchScope, maxImpl: Int, handler: (PsiMethod) -> Unit)

  @RequiresReadLock
  fun findImplementations(interfaceClass: PsiClass, scope: GlobalSearchScope, maxImpl: Int, handler: (PsiClass) -> Unit)

  @RequiresReadLock
  fun inheritsFromAny(psiClass: PsiClass, baseClassNames: Collection<String>): Boolean

  @RequiresReadLock
  fun isInPackages(className: String, packagePrefixes: Collection<String>): Boolean

  @RequiresReadLock
  fun resolveReturnType(method: PsiMethod): PsiClass?

  @RequiresReadLock
  fun extractTypeArguments(type: PsiType): List<PsiType>

  @RequiresReadLock
  fun extractSignature(element: PsiElement): MethodSignature

  @RequiresReadLock
  fun extractTargetElement(file: PsiFile, caretOffset: Int): PsiMethod?
}