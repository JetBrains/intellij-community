// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import org.gradle.util.GradleVersion
import org.jetbrains.jps.model.java.JdkVersionDetector.JdkVersionInfo
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleTestApplication
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleJvmFixture
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path

@GradleTestApplication
abstract class GradleBaseTestCase {

  val gradleVersion: GradleVersion = GradleVersion.current()
  private val javaVersion = JavaVersionRestriction.NO

  private val testDisposable by disposableFixture()

  private val testPathFixture = tempPathFixture()
  val testPath: Path by testPathFixture
  val testRoot: VirtualFile get() = testPath.refreshAndGetVirtualDirectory()

  private val gradleJvmFixture by gradleJvmFixture(gradleVersion, javaVersion)
  val gradleJvm: String get() = gradleJvmFixture.gradleJvm
  val gradleJvmInfo: JdkVersionInfo get() = gradleJvmFixture.gradleJvmInfo

  private val gradleFixture by gradleFixture()

  @BeforeEach
  fun setUpGradleBaseTestCase() {
    gradleJvmFixture.installProjectSettingsConfigurator(testDisposable)
    AutoImportProjectTracker.enableAutoReloadInTests(testDisposable)
    AutoImportProjectTracker.enableAsyncAutoReloadInTests(testDisposable)
    ExternalSystemImportingTestCase.installExecutionOutputPrinter(testDisposable)
  }

  suspend fun openProject(relativePath: String, numProjectSyncs: Int = 1): Project {
    return gradleFixture.openProject(testPath.resolve(relativePath), numProjectSyncs)
  }

  suspend fun linkProject(project: Project, relativePath: String) {
    gradleFixture.linkProject(project, testPath.resolve(relativePath))
  }

  suspend fun reloadProject(project: Project, relativePath: String, configure: ImportSpecBuilder.() -> Unit = {}) {
    gradleFixture.reloadProject(project, testPath.resolve(relativePath), configure)
  }

  suspend fun awaitOpenProjectConfiguration(numProjectSyncs: Int = 1, openProject: suspend () -> Project): Project {
    return gradleFixture.awaitOpenProjectConfiguration(numProjectSyncs, openProject)
  }

  suspend fun <R> awaitProjectConfiguration(project: Project, numProjectSyncs: Int = 1, action: suspend () -> R): R {
    return gradleFixture.awaitProjectConfiguration(project, numProjectSyncs, action)
  }

  fun assertNotificationIsVisible(project: Project, isNotificationVisible: Boolean) {
    gradleFixture.assertNotificationIsVisible(project, isNotificationVisible)
  }
}