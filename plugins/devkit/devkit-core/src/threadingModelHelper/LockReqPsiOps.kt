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
 * A call of [method] in the body of the analyzed method.
 * The [callSite] is the element that the stack trace frame points to, for example the identifier of the called method.
 */
data class MethodCallee(val method: PsiMethod, val callSite: PsiElement)

/**
 * Language-aware interface that provides extraction for different JVM languages used in application code
 */
interface LockReqPsiOps {
  companion object {
    fun forLanguage(language: Language): LockReqPsiOps = forLanguageOrNull(language) ?: error("No LockReqPsiOps for language $language")

    fun forLanguageOrNull(language: Language): LockReqPsiOps? = LockReqPsiOpsProvider.forLanguage(language)
  }

  /**
   * @param timer the implementation measures the reference resolve as [LockReqPhaseTimer.Phase.REFERENCE_RESOLVE]
   * @return one [MethodCallee] for each called method, with the first call site of the method
   */
  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun getMethodCallees(method: PsiMethod, timer: LockReqPhaseTimer = LockReqPhaseTimer.DISABLED): List<MethodCallee>

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun findInheritors(method: PsiMethod, scope: GlobalSearchScope, maxImpl: Int, handler: (PsiMethod) -> Unit)

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun findImplementations(interfaceClass: PsiClass, scope: GlobalSearchScope, maxImpl: Int, handler: (PsiClass) -> Unit)

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun inheritsFromAny(psiClass: PsiClass, baseClassNames: Collection<String>): Boolean

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun isInPackages(className: String, packagePrefixes: Collection<String>): Boolean

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun resolveReturnType(method: PsiMethod): PsiClass?

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun extractTypeArguments(type: PsiType): List<PsiType>

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun extractSignature(element: PsiElement): MethodSignature

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun extractTargetElement(file: PsiFile, caretOffset: Int): PsiMethod?
}