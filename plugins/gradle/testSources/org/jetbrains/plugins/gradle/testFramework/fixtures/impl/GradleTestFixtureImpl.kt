// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl
import com.intellij.openapi.externalSystem.testFramework.fixtures.MultiProjectTestFixture
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.observable.util.setSystemProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestObservation.awaitProjectActivity
import com.intellij.testFramework.common.runAll
import org.gradle.util.GradleVersion
import org.jetbrains.jps.model.java.JdkVersionDetector.JdkVersionInfo
import org.jetbrains.plugins.gradle.connection.GradleConnectorService.Companion.USE_PRODUCTION_DISPOSE_FOR_TESTS_KEY
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.tracker.OperationLeakTracker
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.getGradleProjectReloadOperation
import java.nio.file.Path

class GradleTestFixtureImpl(
  private val multiProjectFixture: MultiProjectTestFixture,
  private val gradleJvmFixture: GradleJvmTestFixture,
  override val gradleVersion: GradleVersion,
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

  override suspend fun <R> withAllowedProjectSyncs(numProjectSyncs: Int, action: suspend () -> R): R {
    return syncLeakTracker.withAllowedOperationAsync(numProjectSyncs, action)
  }

  override suspend fun openProject(projectPath: Path, numProjectSyncs: Int): Project {
    return withAllowedProjectSyncs(numProjectSyncs) {
      multiProjectFixture.openProject(projectPath)
    }
  }

  override suspend fun linkProject(project: Project, projectPath: Path) {
    withAllowedProjectSyncs {
      multiProjectFixture.linkProject(project, projectPath, GradleConstants.SYSTEM_ID)
    }
  }

  override suspend fun syncProject(project: Project, projectPath: Path, configure: ImportSpecBuilder.() -> Unit) {
    withAllowedProjectSyncs {
      awaitProjectActivity(project) {
        ExternalSystemUtil.refreshProject(
          projectPath.toCanonicalPath(),
          ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
            .apply(configure)
        )
      }
    }
  }
}