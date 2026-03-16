// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.driver

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.Couple
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.searches.AllOverridingMethodsSearch
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.toUElementOfType

internal class RemoteMethodImplementationSearcher : QueryExecutor<PsiMethod, OverridingMethodsSearch.SearchParameters> {
  override fun execute(queryParameters: OverridingMethodsSearch.SearchParameters, consumer: Processor<in PsiMethod>): Boolean {
    return ReadAction.compute<Boolean, Throwable> {
      if (queryParameters.method.hasModifier(JvmModifier.ABSTRACT)) {
        if (isRemoteInterface(queryParameters.method.containingClass)) {
          val remoteMethods = findRemoteMethods(queryParameters.method)
          for (m in remoteMethods) {
            if (!consumer.process(m)) {
              return@compute false
            }
          }
        }
      }

      return@compute true
    }
  }
}

internal class RemoteMethodAllImplementationSearcher : QueryExecutor<Couple<PsiMethod>, AllOverridingMethodsSearch.SearchParameters> {
  override fun execute(queryParameters: AllOverridingMethodsSearch.SearchParameters, consumer: Processor<in Couple<PsiMethod>>): Boolean {
    return ReadAction.compute<Boolean, Throwable> {
      if (isRemoteInterface(queryParameters.psiClass)) {
        for (interfaceMethod in queryParameters.psiClass.allMethods) {
          val remoteMethods = findRemoteMethods(interfaceMethod)
          for (m in remoteMethods) {
            if (!consumer.process(Couple(interfaceMethod, m))) {
              return@compute false
            }
          }
        }
      }

      return@compute true
    }
  }
}

internal fun findRemoteMethods(psiMethod: PsiMethod): Collection<PsiMethod> {
  if (!psiMethod.isValid) return emptyList()

  val uClass = psiMethod.toUElementOfType<UMethod>()?.getContainingUClass() ?: return emptyList()
  val remoteClass = getTargetRemoteClass(psiMethod.project, uClass) ?: return emptyList()

  return remoteClass.allMethods
    .filter { it.name == psiMethod.name }
    .filterNot {
      it.hasModifier(JvmModifier.PRIVATE)
      || it.hasModifier(JvmModifier.PROTECTED)
      || it.hasModifier(JvmModifier.PACKAGE_LOCAL)
    }
    .filter { it.parameters.size == psiMethod.parameters.size }
}

internal class RemoteInterfaceDirectImplementationSearcher : QueryExecutor<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  override fun execute(queryParameters: DirectClassInheritorsSearch.SearchParameters, consumer: Processor<in PsiClass>): Boolean {
    return processRemoteTargetClass(queryParameters.classToProcess, consumer)
  }
}

internal class RemoteInterfaceImplementationSearcher : QueryExecutor<PsiClass, ClassInheritorsSearch.SearchParameters> {
  override fun execute(queryParameters: ClassInheritorsSearch.SearchParameters, consumer: Processor<in PsiClass>): Boolean {
    return processRemoteTargetClass(queryParameters.classToProcess, consumer)
  }
}

private fun processRemoteTargetClass(
  classToProcess: PsiClass,
  consumer: Processor<in PsiClass>,
): Boolean {
  return ReadAction.compute<Boolean, Throwable> {
    if (isRemoteInterface(classToProcess)) {
      val uClass = classToProcess.toUElementOfType<UClass>() ?: return@compute true
      val targetClass = getTargetRemoteClass(classToProcess.project, uClass)
      if (targetClass != null) {
        if (!consumer.process(targetClass)) {
          return@compute false
        }
      }
    }
    return@compute true
  }
}