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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class KtLockReqPsiOps() : LockReqPsiOps {

  private val resolver: KtCallResolver = KtFirUastCallResolver()

  override fun getMethodCallees(method: PsiMethod): List<PsiMethod> {
    return resolver.resolveCallees(method).distinct()
  }

  override fun findInheritors(method: PsiMethod, scope: GlobalSearchScope, maxImpl: Int, handler: (PsiMethod) -> Unit) {
    if (method.body != null) {
      handler(method)
    }
    val counter = AtomicInteger(1)
    val list = mutableListOf<PsiMethod>()
    val abruptEnd: AtomicBoolean = AtomicBoolean(false)
    OverridingMethodsSearch.search(method, scope, true)
      .forEach(Processor { overridden ->
        if (counter.incrementAndGet() >= maxImpl) {
          //println("Too many inheritors for ${method.name}, stopping")
          abruptEnd.set(true)
          return@Processor false
        }
        list.add(overridden)
        true
      })
    if (!abruptEnd.get()) {
      for (method in list) {
        handler(method)
      }
    }
  }

  override fun findImplementations(interfaceClass: PsiClass, scope: GlobalSearchScope, maxImpl: Int, handler: (PsiClass) -> Unit) {
    val counter = AtomicInteger(1)
    val list = mutableListOf<PsiClass>()
    val abruptEnd: AtomicBoolean = AtomicBoolean(false)
    ClassInheritorsSearch.search(interfaceClass, scope, true)
      .forEach(Processor { implementor ->
        if (counter.incrementAndGet() >= maxImpl) {
          //println("Too many implementations for ${interfaceClass.name}, stopping")
          abruptEnd.set(true)
          return@Processor false
        }
        list.add(implementor)
        true
      })
    if (!abruptEnd.get()) {
      for (clazz in list) {
        handler(clazz)
      }
    }
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