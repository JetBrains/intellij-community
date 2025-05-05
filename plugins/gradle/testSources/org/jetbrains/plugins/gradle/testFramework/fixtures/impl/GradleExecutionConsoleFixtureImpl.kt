// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.build.BuildView
import com.intellij.execution.Location
import com.intellij.execution.junit2.PsiMemberParameterizedLocation
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.openapi.project.Project
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.platform.testFramework.assertion.buildViewAssertions.BuildViewAssertions
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionConsoleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionEnvironmentFixture
import org.junit.jupiter.api.Assertions

class GradleExecutionConsoleFixtureImpl(
  private val project: Project,
  private val executionEnvironmentFixture: GradleExecutionEnvironmentFixture,
) : GradleExecutionConsoleFixture {

  override fun setUp() = Unit

  override fun tearDown() = Unit

  override fun assertRunTreeView(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    val executionEnvironment = executionEnvironmentFixture.getExecutionEnvironment()
    val buildView = executionEnvironment.contentToReuse!!.executionConsole!! as BuildView
    BuildViewAssertions.assertBuildViewTree(buildView, assert)
  }

  override fun assertRunTreeViewIsEmpty() {
    assertRunTreeView {}
  }

  override fun assertPsiLocation(
    testAssertion: SimpleTreeAssertion.Node<AbstractTestProxy>,
    className: String, methodName: String?, parameterName: String?,
  ) {
    testAssertion.assertValue { testProxy ->
      val location = testProxy.getLocation()
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

  private fun AbstractTestProxy.getLocation(): Location<*> {
    val location = getLocation(project, GlobalSearchScope.allScope(project))
    Assertions.assertNotNull(location) { "Cannot resolve location for $locationUrl" }
    return location
  }
}