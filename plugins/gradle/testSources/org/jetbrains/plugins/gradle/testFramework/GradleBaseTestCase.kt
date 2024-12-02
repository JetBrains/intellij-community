// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.runAll
import org.gradle.util.GradleVersion
import org.jetbrains.jps.model.java.JdkVersionDetector.JdkVersionInfo
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleTestApplication
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleTestFixtureImpl
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo

@GradleTestApplication
abstract class GradleBaseTestCase {

  private lateinit var testDisposable: Disposable

  private lateinit var gradleTestFixture: GradleTestFixture

  val testRoot: VirtualFile get() = gradleTestFixture.testRoot
  val gradleJvm: String get() = gradleTestFixture.gradleJvm
  val gradleJvmInfo: JdkVersionInfo get() = gradleTestFixture.gradleJvmInfo
  val gradleVersion: GradleVersion get() = gradleTestFixture.gradleVersion

  @BeforeEach
  fun setUpGradleBaseTestCase(testInfo: TestInfo) {
    gradleTestFixture = GradleTestFixtureImpl(
        className = testInfo.testClass.get().simpleName,
        methodName = testInfo.testMethod.get().name,
        gradleVersion = GradleVersion.current(),
        javaVersionRestriction = JavaVersionRestriction.NO
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

  suspend fun openProject(relativePath: String, numProjectSyncs: Int = 1): Project {
    return gradleTestFixture.openProject(relativePath, numProjectSyncs)
  }

  suspend fun linkProject(project: Project, relativePath: String) {
    gradleTestFixture.linkProject(project, relativePath)
  }

  suspend fun reloadProject(project: Project, relativePath: String, configure: ImportSpecBuilder.() -> Unit = {}) {
    gradleTestFixture.reloadProject(project, relativePath, configure)
  }

  suspend fun awaitOpenProjectConfiguration(numProjectSyncs: Int = 1, openProject: suspend () -> Project): Project {
    return gradleTestFixture.awaitOpenProjectConfiguration(numProjectSyncs, openProject)
  }

  suspend fun <R> awaitProjectConfiguration(project: Project, numProjectSyncs: Int = 1, action: suspend () -> R): R {
    return gradleTestFixture.awaitProjectConfiguration(project, numProjectSyncs, action)
  }

  fun assertNotificationIsVisible(project: Project, isNotificationVisible: Boolean) {
    gradleTestFixture.assertNotificationIsVisible(project, isNotificationVisible)
  }
}