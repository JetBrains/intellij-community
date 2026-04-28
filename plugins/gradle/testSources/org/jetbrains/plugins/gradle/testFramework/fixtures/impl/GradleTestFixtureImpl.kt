// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectNotificationAware
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.autolink.UnlinkedProjectStartupActivity
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.observable.util.setSystemProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestObservation.awaitOpenProjectActivity
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestObservation.awaitProjectActivity
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.openProjectAsync
import org.jetbrains.jps.model.java.JdkVersionDetector.JdkVersionInfo
import org.jetbrains.plugins.gradle.connection.GradleConnectorService.Companion.USE_PRODUCTION_DISPOSE_FOR_TESTS_KEY
import org.jetbrains.plugins.gradle.service.project.open.linkAndSyncGradleProject
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.tracker.OperationLeakTracker
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.getGradleProjectReloadOperation
import org.junit.jupiter.api.Assertions
import java.nio.file.Path

class GradleTestFixtureImpl(
  private val gradleJvmFixture: GradleJvmTestFixture,
) : GradleTestFixture {

  private lateinit var syncLeakTracker: OperationLeakTracker

  private lateinit var testDisposable: Disposable

  override val gradleJvm: String by gradleJvmFixture::gradleJvm
  override val gradleJvmPath: String by gradleJvmFixture::gradleJvmPath
  override val gradleJvmInfo: JdkVersionInfo by gradleJvmFixture::gradleJvmInfo

  fun setUp() {
    syncLeakTracker = OperationLeakTracker { getGradleProjectReloadOperation(it) }
    syncLeakTracker.setUp()

    testDisposable = Disposer.newDisposable()

    AutoImportProjectTracker.enableAutoReloadInTests(testDisposable)
    AutoImportProjectTracker.enableAsyncAutoReloadInTests(testDisposable)
    ExternalSystemImportingTestCase.installExecutionOutputPrinter(testDisposable)
    gradleJvmFixture.installProjectSettingsConfigurator(testDisposable)

    setSystemProperty(USE_PRODUCTION_DISPOSE_FOR_TESTS_KEY, true.toString(), testDisposable)
  }

  fun tearDown() {
    runAll(
      { Disposer.dispose(testDisposable) },
      { syncLeakTracker.tearDown() },
      { ExternalSystemProgressNotificationManagerImpl.assertListenersReleased() },
      { ExternalSystemProgressNotificationManagerImpl.cleanupListeners() }
    )
  }

  override suspend fun openProject(projectPath: Path, numProjectSyncs: Int): Project {
    return awaitOpenProjectConfiguration(numProjectSyncs) {
      openProjectAsync(projectPath, UnlinkedProjectStartupActivity())
    }
  }

  override suspend fun linkProject(project: Project, projectPath: Path) {
    awaitProjectConfiguration(project) {
      linkAndSyncGradleProject(project, projectPath.toCanonicalPath())
    }
  }

  override suspend fun syncProject(project: Project, projectPath: Path, configure: ImportSpecBuilder.() -> Unit) {
    awaitProjectConfiguration(project) {
      ExternalSystemUtil.refreshProject(
        projectPath.toCanonicalPath(),
        ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
          .apply(configure)
      )
    }
  }

  override suspend fun awaitOpenProjectConfiguration(numProjectSyncs: Int, openProject: suspend () -> Project): Project {
    return closeOpenedProjectsIfFailAsync {
      syncLeakTracker.withAllowedOperationAsync(numProjectSyncs) {
        awaitOpenProjectActivity(openProject)
      }
    }
  }

  override suspend fun <R> awaitProjectConfiguration(project: Project, numProjectSyncs: Int, action: suspend () -> R): R {
    return syncLeakTracker.withAllowedOperationAsync(numProjectSyncs) {
      awaitProjectActivity(project, action)
    }
  }

  override fun assertNotificationIsVisible(project: Project, isNotificationVisible: Boolean) {
    val notificationAware = AutoImportProjectNotificationAware.getInstance(project)
    Assertions.assertEquals(isNotificationVisible, notificationAware.isNotificationVisible()) {
      notificationAware.getProjectsWithNotification().toString()
    }
  }
}