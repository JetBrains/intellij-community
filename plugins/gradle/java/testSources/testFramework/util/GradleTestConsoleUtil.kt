// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTree
import com.intellij.platform.testFramework.assertion.treeAssertion.buildTree
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole
import org.junit.jupiter.api.Assertions

val ExecutionEnvironment.testConsole: GradleTestsExecutionConsole
  get() {
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

val GradleTestsExecutionConsole.consoleText: String
  get() = resultsViewer.root.consoleText

val GradleTestsExecutionConsole.testProxyTree: SimpleTree<AbstractTestProxy>
  get() = buildTree(resultsViewer.root.children, { name }, { children })