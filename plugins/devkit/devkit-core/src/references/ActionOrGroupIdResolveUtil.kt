// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references

import com.intellij.execution.Executor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScopesCore.projectProductionScope
import com.intellij.psi.search.ProjectScope.getLibrariesScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.util.PairProcessor
import org.jetbrains.idea.devkit.util.PsiUtil
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.evaluateString

internal object ActionOrGroupIdResolveUtil {

  @JvmStatic
  fun processExecutors(project: Project, processor: PairProcessor<String, PsiClass>) {
    // not necessarily in the element's resolve scope
    val scope = projectProductionScope(project).union(getLibrariesScope(project))
    val executorClass = JavaPsiFacade.getInstance(project).findClass(Executor::class.java.getName(), scope)
    if (executorClass == null) return

    for (inheritor in ClassInheritorsSearch.search(executorClass, scope, true)) {
      val id = computeConstantReturnValue(inheritor, "getId")
      if (id != null && !processor.process(id, inheritor)) return

      val contextActionId = computeConstantReturnValue(inheritor, "getContextActionId") ?: continue
      if (!processor.process(contextActionId, inheritor)) return
    }

    // fallback for no source
    for (executor in Executor.EXECUTOR_EXTENSION_NAME.extensionList) {
      val executorPsiClass = JavaPsiFacade.getInstance(project).findClass(executor.javaClass.getName(), scope)
      if (executorPsiClass == null) continue

      if (!processor.process(executor.id, executorPsiClass)) return
      if (executor.contextActionId != null && !processor.process(executor.contextActionId, executorPsiClass)) return
    }
  }

  private fun computeConstantReturnValue(
    psiClass: PsiClass,
    methodName: String,
  ): String? {
    val expression = getReturnExpression(psiClass, methodName)
    if (expression == null) return null

    return expression.evaluateString()
  }

  @JvmStatic
  fun getReturnExpression(psiClass: PsiClass, methodName: String): UExpression? {
    val methods = psiClass.findMethodsByName(methodName, false)
    if (methods.size != 1) {
      return null
    }

    return PsiUtil.getReturnedExpression(methods[0])
  }

}