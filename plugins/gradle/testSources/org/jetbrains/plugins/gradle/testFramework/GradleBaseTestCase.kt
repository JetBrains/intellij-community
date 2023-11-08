// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.junit5.TestApplication
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo

@TestApplication
abstract class GradleBaseTestCase {

  private lateinit var testDisposable: Disposable

  private lateinit var gradleTestFixture: GradleTestFixture

  val testRoot: VirtualFile get() = gradleTestFixture.testRoot
  val gradleJvm: String get() = gradleTestFixture.gradleJvm
  val gradleVersion: GradleVersion get() = gradleTestFixture.gradleVersion

  @BeforeEach
  fun setUpGradleBaseTestCase(testInfo: TestInfo) {
    gradleTestFixture = GradleTestFixtureFactory.getFixtureFactory()
      .createGradleTestFixture(
        className = testInfo.testClass.get().simpleName,
        methodName = testInfo.testMethod.get().name,
        gradleVersion = GradleVersion.current()
      )
    gradleTestFixture.setUp()

    testDisposable = Disposer.newDisposable()
    AutoImportProjectTracker.enableAutoReloadInTests(testDisposable)
    AutoImportProjectTracker.enableAsyncAutoReloadInTests(testDisposable)
  }

  @AfterEach
  fun tearDownGradleBaseTestCase() {
    runAll(
      { Disposer.dispose(testDisposable) },
      { gradleTestFixture.tearDown() }
    )
  }

  suspend fun openProject(relativePath: String, wait: Boolean = true): Project {
    return gradleTestFixture.openProject(relativePath, wait)
  }

  suspend fun linkProject(project: Project, relativePath: String) {
    gradleTestFixture.linkProject(project, relativePath)
  }

  suspend fun reloadProject(project: Project, relativePath: String, configure: ImportSpecBuilder.() -> Unit) {
    gradleTestFixture.reloadProject(project, relativePath, configure)
  }

  suspend fun <R> awaitAnyGradleProjectReload(wait: Boolean = true, action: suspend () -> R): R {
    return gradleTestFixture.awaitAnyGradleProjectReload(wait, action)
  }

  fun assertNotificationIsVisible(project: Project, isNotificationVisible: Boolean) {
    gradleTestFixture.assertNotificationIsVisible(project, isNotificationVisible)
  }
}