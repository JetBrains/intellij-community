// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleTestApplication
import org.jetbrains.plugins.gradle.testFramework.fixtures.buildViewFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.junit.jupiter.api.BeforeEach

@GradleTestApplication
abstract class GradleSyncOutputTestCase {

  private val testDisposable by disposableFixture()

  private val testRootFixture = tempPathFixture()
  val testRoot by testRootFixture

  private val projectFixture = projectFixture(testRootFixture, openAfterCreation = true)
  val project by projectFixture

  val gradleFixture by gradleFixture()

  val buildViewFixture by buildViewFixture(projectFixture)

  @BeforeEach
  fun setUpGradleReloadProjectBaseTestCase() {
    AutoImportProjectTracker.enableAutoReloadInTests(testDisposable)
    AutoImportProjectTracker.enableAsyncAutoReloadInTests(testDisposable)
    ExternalSystemImportingTestCase.installExecutionOutputPrinter(testDisposable)
  }

  fun createSettingsFile(gradleVersion: GradleVersion, configure: GradleSettingScriptBuilder<*>.() -> Unit) =
    testRoot.createSettingsFile(gradleVersion, configure = configure)

  fun createBuildFile(gradleVersion: GradleVersion, configure: GradleBuildScriptBuilder<*>.() -> Unit) =
    testRoot.createBuildFile(gradleVersion, configure = configure)

  suspend fun reloadProject() {
    gradleFixture.reloadProject(project, testRoot)
  }

  fun assertSyncViewTree(assert: SimpleTreeAssertion.Node<Nothing?>.() -> Unit) {
    buildViewFixture.assertSyncViewTree(assert)
  }
}