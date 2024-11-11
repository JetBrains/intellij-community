// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixture

import com.intellij.build.BuildTreeConsoleView
import com.intellij.build.BuildView
import com.intellij.execution.Location
import com.intellij.execution.junit2.PsiMemberParameterizedLocation
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixture
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole
import com.intellij.platform.testFramework.treeAssertion.SimpleTree
import com.intellij.platform.testFramework.treeAssertion.SimpleTreeAssertion
import com.intellij.platform.testFramework.treeAssertion.buildTree
import org.junit.jupiter.api.AssertionFailureBuilder
import org.junit.jupiter.api.Assertions

class GradleExecutionViewFixture(
  private val project: Project,
  private val executionEnvironmentFixture: GradleExecutionEnvironmentFixture
) : IdeaTestFixture {

  override fun setUp() = Unit

  override fun tearDown() = Unit

  fun getTestExecutionConsole(): GradleTestsExecutionConsole {
    val executionEnvironment = executionEnvironmentFixture.getExecutionEnvironment()
    val buildView = executionEnvironment.contentToReuse!!.executionConsole!! as BuildView
    val testExecutionConsole = buildView.consoleView as? GradleTestsExecutionConsole
    Assertions.assertNotNull(testExecutionConsole) {
      "Test view isn't shown"
    }
    val treeView = testExecutionConsole!!.resultsViewer.treeView!!
    invokeAndWaitIfNeeded {
      PlatformTestUtil.waitWhileBusy(treeView)
      TestConsoleProperties.HIDE_PASSED_TESTS.set(testExecutionConsole.properties, false)
      PlatformTestUtil.waitWhileBusy(treeView)
      PlatformTestUtil.expandAll(treeView)
      PlatformTestUtil.waitWhileBusy(treeView)
    }
    return testExecutionConsole
  }

  private fun getSimplifiedRunTreeView(): SimpleTree<Nothing?> {
    val executionEnvironment = executionEnvironmentFixture.getExecutionEnvironment()
    val buildView = executionEnvironment.contentToReuse!!.executionConsole!! as BuildView
    val eventView = buildView.getView(BuildTreeConsoleView::class.java.name, BuildTreeConsoleView::class.java)
    Assertions.assertNotNull(eventView) {
      "Run view isn't shown"
    }
    val treeView = eventView!!.tree!!
    invokeAndWaitIfNeeded {
      PlatformTestUtil.waitWhileBusy(treeView)
      eventView.addFilter { true }
      PlatformTestUtil.waitWhileBusy(treeView)
      PlatformTestUtil.expandAll(treeView)
      PlatformTestUtil.waitWhileBusy(treeView)
    }
    val treeString = invokeAndWaitIfNeeded {
      PlatformTestUtil.print(treeView, false)
    }
    invokeAndWaitIfNeeded {
      PlatformTestUtil.waitWhileBusy(treeView)
    }
    return buildTree(treeString)
  }

  private fun getTestConsoleText(): String {
    val testExecutionConsole = getTestExecutionConsole()
    val rootProxy = testExecutionConsole.resultsViewer.root
    return getTestConsoleText(rootProxy)
  }

  private fun getTestConsoleText(testProxy: AbstractTestProxy): String {
    val printer = MockPrinter()
    printer.setShowHyperLink(true)
    testProxy.printOn(printer)
    return printer.allOut
  }

  fun assertTestConsoleContains(expectedTextSample: String) {
    assertContains(expectedTextSample, getTestConsoleText())
  }

  fun assertTestConsoleDoesNotContain(unexpectedTextSample: String) {
    assertDoesNotContain(unexpectedTextSample, getTestConsoleText())
  }

  fun assertTestConsoleContains(testAssertion: SimpleTreeAssertion.Node<AbstractTestProxy>, expectedTextSample: String) {
    testAssertion.assertValue { testProxy ->
      assertContains(expectedTextSample, getTestConsoleText(testProxy))
    }
  }

  fun assertTestConsoleDoesNotContain(testAssertion: SimpleTreeAssertion.Node<AbstractTestProxy>, unexpectedTextSample: String) {
    testAssertion.assertValue { testProxy ->
      assertDoesNotContain(unexpectedTextSample, getTestConsoleText(testProxy))
    }
  }

  fun assertRunTreeView(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    SimpleTreeAssertion.assertTree(getSimplifiedRunTreeView()) {
      assertNode("", assert = assert)
    }
  }

  fun assertTestTreeView(assert: SimpleTreeAssertion<AbstractTestProxy>.() -> Unit) {
    val testExecutionConsole = getTestExecutionConsole()
    val resultsViewer = testExecutionConsole.resultsViewer
    val roots = resultsViewer.root.children
    val tree = buildTree(roots, { name }, { children })
    runReadAction { // all navigation tests requires read action
      SimpleTreeAssertion.assertUnorderedTree(tree, assert)
    }
  }

  fun assertRunTreeViewIsEmpty() {
    assertRunTreeView {}
  }

  fun assertTestTreeViewIsEmpty() {
    assertTestTreeView {}
  }

  fun assertPsiLocation(
    testAssertion: SimpleTreeAssertion.Node<AbstractTestProxy>,
    className: String, methodName: String?, parameterName: String?
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

  private fun assertContains(expectedTextSample: String, actualText: String) {
    val canonicalExpectedTextSample = StringUtil.convertLineSeparators(expectedTextSample)
    val canonicalActualText = StringUtil.convertLineSeparators(actualText)
    if (canonicalExpectedTextSample !in canonicalActualText) {
      throw AssertionFailureBuilder.assertionFailure()
        .message("Text doesn't contain text sample but should")
        .expected(canonicalExpectedTextSample)
        .actual(canonicalActualText)
        .build()
    }
  }

  private fun assertDoesNotContain(unexpectedTextSample: String, actualText: String) {
    val canonicalUnexpectedTextSample = StringUtil.convertLineSeparators(unexpectedTextSample)
    val canonicalActualText = StringUtil.convertLineSeparators(actualText)
    if (canonicalUnexpectedTextSample in canonicalActualText) {
      throw AssertionFailureBuilder.assertionFailure()
        .message("Text contains text sample but shouldn't")
        .expected(canonicalUnexpectedTextSample)
        .actual(canonicalActualText)
        .build()
    }
  }
}