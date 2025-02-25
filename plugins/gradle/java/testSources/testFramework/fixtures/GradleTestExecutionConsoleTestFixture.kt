// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.testFramework.fixtures.IdeaTestFixture
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole

interface GradleTestExecutionConsoleTestFixture : IdeaTestFixture {

  fun getTestExecutionConsole(): GradleTestsExecutionConsole

  fun assertTestConsoleContains(expectedTextSample: String)

  fun assertTestConsoleDoesNotContain(unexpectedTextSample: String)

  fun assertTestConsoleContains(testAssertion: SimpleTreeAssertion.Node<AbstractTestProxy>, expectedTextSample: String)

  fun assertTestConsoleDoesNotContain(testAssertion: SimpleTreeAssertion.Node<AbstractTestProxy>, unexpectedTextSample: String)

  fun assertTestTreeView(assert: SimpleTreeAssertion<AbstractTestProxy>.() -> Unit)

  fun assertTestTreeViewIsEmpty()
}