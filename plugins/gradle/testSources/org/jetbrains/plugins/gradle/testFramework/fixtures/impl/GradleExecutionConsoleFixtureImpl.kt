// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.build.BuildView
import com.intellij.platform.testFramework.assertion.buildViewAssertions.BuildViewAssertions
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionConsoleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleExecutionEnvironmentFixture

class GradleExecutionConsoleFixtureImpl(
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
}