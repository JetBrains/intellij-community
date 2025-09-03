// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.psi.PsiMethod
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.util.Processor
import kotlin.time.measureTime


object LockReqsPsiOps {

  fun getMethodCallees(method: PsiMethod): List<PsiMethod> {
    val callees = mutableListOf<PsiMethod>()

    method.body?.accept(object : JavaRecursiveElementVisitor() {
      override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
        super.visitMethodCallExpression(expression)
        expression.resolveMethod()?.let { callees.add(it) }
      }

      override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
        super.visitMethodReferenceExpression(expression)
        (expression.resolve() as? PsiMethod)?.let { callees.add(it) }
      }

      override fun visitNewExpression(expression: PsiNewExpression) {
        super.visitNewExpression(expression)
        expression.resolveMethod()?.let { callees.add(it) }
      }
    })

    return callees
  }

  fun findInheritors(method: PsiMethod, scope: GlobalSearchScope, maxImpl: Int): List<PsiMethod> {
    val inheritors = mutableListOf<PsiMethod>()
    if (method.body != null) {
      inheritors.add(method)
    }
    measureTime {
      val query = OverridingMethodsSearch.search(method, scope, true)
      query.allowParallelProcessing().forEach(Processor { overridden ->
        if (inheritors.size >= maxImpl) return@Processor false
        inheritors.add(overridden)
      })
    }.let { println("findInheritors: ${it}") }

      return inheritors
    }

    fun findImplementations(interfaceClass: PsiClass, scope: GlobalSearchScope, maxImpl: Int): List<PsiClass> {
      val implementations = mutableListOf<PsiClass>()
      val query = ClassInheritorsSearch.search(interfaceClass, scope, true)
      query.allowParallelProcessing().forEach(Processor { implementor ->
        if (implementations.size >= maxImpl) return@Processor false
        implementations.add(implementor)
      })
      return implementations
    }

    fun inheritsFromAny(psiClass: PsiClass, baseClassNames: Collection<String>): Boolean {
      return baseClassNames.any { baseClassName ->
        InheritanceUtil.isInheritor(psiClass, baseClassName)
      }
    }

    fun isInPackages(className: String, packagePrefixes: Collection<String>): Boolean {
      return packagePrefixes.any { prefix -> className.startsWith("$prefix.") }
    }

    fun resolveReturnType(method: PsiMethod): PsiClass? {
      val returnType = method.returnType as? PsiClassType ?: return null
      return returnType.resolve()
    }

    fun extractTypeArguments(type: PsiType): List<PsiType> {
      return (type as? PsiClassType)?.parameters?.toList() ?: emptyList()
    }
  }
