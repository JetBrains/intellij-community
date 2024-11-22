// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BuildViewTestFixture
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase.Companion.assertNodeWithDeprecatedGradleWarning
import org.jetbrains.plugins.gradle.importing.syncAction.GradleProjectResolverTestCase

abstract class GradleActionWithImportTestCase : GradleProjectResolverTestCase() {

  private lateinit var buildViewTestFixture: BuildViewTestFixture

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    buildViewTestFixture = BuildViewTestFixture(myProject)
    buildViewTestFixture.setUp()
  }

  override fun tearDown() = RunAll(
    ThrowableRunnable { if (::buildViewTestFixture.isInitialized) buildViewTestFixture.tearDown() },
    ThrowableRunnable { super.tearDown() }
  ).run()

  fun SimpleTreeAssertion.Node<Nothing?>.assertNodeWithDeprecatedGradleWarning() {
    assertNodeWithDeprecatedGradleWarning(currentGradleVersion)
  }

  fun assertSyncViewTree(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    buildViewTestFixture.assertSyncViewTree(assert)
  }
}
