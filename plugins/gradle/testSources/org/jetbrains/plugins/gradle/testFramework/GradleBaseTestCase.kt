// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.jps.model.java.JdkVersionDetector.JdkVersionInfo
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleTestApplication
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleJvmTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleTestFixtureImpl
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo

@GradleTestApplication
abstract class GradleBaseTestCase {

  val gradleVersion: GradleVersion = GradleVersion.current()
  private val javaVersion = JavaVersionRestriction.NO

  private lateinit var testDisposable: Disposable

  private lateinit var fileFixture: TempDirTestFixture
  lateinit var testRoot: VirtualFile
    private set

  private lateinit var gradleJvmFixture: GradleJvmTestFixture
  val gradleJvm: String get() = gradleJvmFixture.gradleJvm
  val gradleJvmInfo: JdkVersionInfo get() = gradleJvmFixture.gradleJvmInfo

  private lateinit var gradleFixture: GradleTestFixture

  @BeforeEach
  fun setUpGradleBaseTestCase(testInfo: TestInfo) {
    gradleJvmFixture = GradleJvmTestFixture(gradleVersion, javaVersion)
    gradleJvmFixture.setUp()
    gradleJvmFixture.installProjectSettingsConfigurator()

    fileFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    fileFixture.setUp()
    testRoot = runBlocking {
      edtWriteAction {
        fileFixture.findOrCreateDir(testInfo.displayName)
          .findOrCreateDirectory(gradleVersion.version)
      }
    }

    gradleFixture = GradleTestFixtureImpl(testRoot)
    gradleFixture.setUp()

    testDisposable = Disposer.newDisposable()
    AutoImportProjectTracker.enableAutoReloadInTests(testDisposable)
    AutoImportProjectTracker.enableAsyncAutoReloadInTests(testDisposable)
    ExternalSystemImportingTestCase.installExecutionOutputPrinter(testDisposable)
  }

  @AfterEach
  fun tearDownGradleBaseTestCase() {
    runAll(
      { Disposer.dispose(testDisposable) },
      { gradleFixture.tearDown() },
      { fileFixture.tearDown() },
      { gradleJvmFixture.tearDown() },
    )
  }

  suspend fun openProject(relativePath: String, numProjectSyncs: Int = 1): Project {
    return gradleFixture.openProject(relativePath, numProjectSyncs)
  }

  suspend fun linkProject(project: Project, relativePath: String) {
    gradleFixture.linkProject(project, relativePath)
  }

  suspend fun reloadProject(project: Project, relativePath: String, configure: ImportSpecBuilder.() -> Unit = {}) {
    gradleFixture.reloadProject(project, relativePath, configure)
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