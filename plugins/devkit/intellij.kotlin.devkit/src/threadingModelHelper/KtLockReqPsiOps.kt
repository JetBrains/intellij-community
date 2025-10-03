// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.threadingModelHelper

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.util.Processor
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqPsiOps

class KtLockReqPsiOps(private val resolver: KtCallResolver = KtFirUastCallResolver()) : LockReqPsiOps {

  override fun getMethodCallees(method: PsiMethod): List<PsiMethod> {
    return resolver.resolveCallees(method).distinct()
  }

  override fun findInheritors(method: PsiMethod, scope: GlobalSearchScope, maxImpl: Int): List<PsiMethod> {
    val inheritors = mutableListOf<PsiMethod>()
    if (method.body != null) {
      inheritors.add(method)
    }
    OverridingMethodsSearch.search(method, scope, true)
      .allowParallelProcessing()
      .forEach(Processor { overridden ->
        if (inheritors.size >= maxImpl) return@Processor false
        inheritors.add(overridden)
        true
      })
    return inheritors
  }

  override fun findImplementations(interfaceClass: PsiClass, scope: GlobalSearchScope, maxImpl: Int): List<PsiClass> {
    val implementations = mutableListOf<PsiClass>()
    ClassInheritorsSearch.search(interfaceClass, scope, true)
      .allowParallelProcessing()
      .forEach(Processor { implementor ->
        if (implementations.size >= maxImpl) return@Processor false
        implementations.add(implementor)
        true
      })
    return implementations
  }

  override fun inheritsFromAny(psiClass: PsiClass, baseClassNames: Collection<String>): Boolean {
    return baseClassNames.any { base -> InheritanceUtil.isInheritor(psiClass, base) }
  }

  override fun isInPackages(className: String, packagePrefixes: Collection<String>): Boolean {
    return packagePrefixes.any { prefix -> className.startsWith("$prefix.") }
  }

  override fun resolveReturnType(method: PsiMethod): PsiClass? {
    return resolver.resolveReturnPsiClass(method)
  }

  override fun extractTypeArguments(type: PsiType): List<PsiType> {
    return (type as? PsiClassType)?.parameters?.toList() ?: emptyList()
  }
}