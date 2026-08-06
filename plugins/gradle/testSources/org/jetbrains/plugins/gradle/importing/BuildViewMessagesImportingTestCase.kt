// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.build.BuildView
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.platform.testFramework.assertion.BuildViewNodeAssertion
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BuildViewTestFixture
import com.intellij.util.ThrowableRunnable
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.util.GradleConstants

abstract class BuildViewMessagesImportingTestCase : GradleImportingTestCase() {

  private lateinit var buildViewTestFixture: BuildViewTestFixture
  val syncView: BuildView get() = buildViewTestFixture.syncView
  val buildView: BuildView get() = buildViewTestFixture.buildView

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    currentExternalProjectSettings.delegatedBuild = true
    useProjectTaskManager = true
    buildViewTestFixture = BuildViewTestFixture(myProject)
    buildViewTestFixture.setUp()
  }

  override fun tearDown() = RunAll(
    ThrowableRunnable { if (::buildViewTestFixture.isInitialized) buildViewTestFixture.tearDown() },
    ThrowableRunnable { super.tearDown() }
  ).run()

  protected fun BuildViewNodeAssertion.assertNodeWithDeprecatedGradleWarning() {
    assertNodeWithDeprecatedGradleWarning(currentGradleVersion)
  }

  protected fun assertSyncViewRerunActions() {
    val rerunActions = buildViewTestFixture.getSyncViewRerunActions()
    assertSize(1, rerunActions)
    val reimportActionText = ExternalSystemBundle.message("action.refresh.project.text", GradleConstants.SYSTEM_ID.readableName)
    assertEquals(reimportActionText, rerunActions[0].templateText)
  }

  override fun handleImportFailure(errorMessage: String, errorDetails: String?) {
    // do not fail tests with failed builds
  }

  companion object {

    fun BuildViewNodeAssertion.assertNodeWithDeprecatedGradleWarning(gradleVersion: GradleVersion) {
      if (GradleJvmSupportMatrix.isGradleDeprecatedByIdea(gradleVersion)) {
        assertNode("Deprecated Gradle Version")
      }
    }
  }
}
