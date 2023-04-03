// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import org.junit.jupiter.api.Assertions

abstract class GradleTestNavigationTestCase : GradleExecutionTestCase() {

  val PsiMethod.psiClass
    get() = containingClass!!

  val AbstractTestProxy.psiClass
    get() = getPsiElement<PsiClass>()

  val AbstractTestProxy.psiMethod
    get() = getPsiElement<PsiMethod>()

  private inline fun <reified T : PsiElement> AbstractTestProxy.getPsiElement(): T {
    val location = getLocation(project, GlobalSearchScope.allScope(project))
    Assertions.assertNotNull(location) { "Cannot resolve location for $locationUrl" }
    return location.psiElement as T
  }
}
