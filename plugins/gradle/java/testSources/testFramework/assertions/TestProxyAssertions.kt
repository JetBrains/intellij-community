// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.assertions

import com.intellij.execution.junit2.PsiMemberParameterizedLocation
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import org.junit.jupiter.api.Assertions

object TestProxyAssertions {

  fun assertPsiLocation(
    project: Project,
    testProxy: AbstractTestProxy,
    className: String,
    methodName: String? = null,
    parameterName: String? = null,
  ) {
    val location = runReadAction {
      testProxy.getLocation(project, GlobalSearchScope.allScope(project))
    }
    Assertions.assertNotNull(location) {
      "Cannot resolve location for ${testProxy.locationUrl}"
    }
    if (methodName == null) {
      val psiClass = location.psiElement as PsiClass
      Assertions.assertEquals(className, psiClass.name)
    }
    else {
      val psiMethod = location.psiElement as PsiMethod
      Assertions.assertEquals(methodName, psiMethod.name)
      Assertions.assertEquals(className, psiMethod.containingClass?.name)
    }
    if (parameterName == null) {
      Assertions.assertTrue(location !is PsiMemberParameterizedLocation) {
        "Test location is parameterized but shouldn't"
      }
    }
    else {
      Assertions.assertTrue(location is PsiMemberParameterizedLocation) {
        "Test location isn't parameterized but should"
      }
    }
    if (parameterName != null) {
      location as PsiMemberParameterizedLocation
      Assertions.assertEquals(parameterName, location.paramSetName)
    }
  }
}