// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectNotificationAware
import com.intellij.openapi.externalSystem.autolink.UnlinkedProjectStartupActivity
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.SdkTestFixture
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.openProjectAsync
import com.intellij.testFramework.utils.vfs.getDirectory
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.project.open.linkAndRefreshGradleProject
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory
import org.jetbrains.plugins.gradle.testFramework.fixtures.tracker.ESListenerLeakTracker
import org.jetbrains.plugins.gradle.testFramework.fixtures.tracker.OperationLeakTracker
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.getGradleProjectReloadOperation
import org.junit.jupiter.api.Assertions

class GradleTestFixtureImpl(
  private val className: String,
  private val methodName: String,
  override val gradleVersion: GradleVersion,
) : GradleTestFixture {

  private lateinit var listenerLeakTracker: ESListenerLeakTracker
  private lateinit var reloadLeakTracker: OperationLeakTracker

  private lateinit var testDisposable: Disposable

  private lateinit var sdkFixture: SdkTestFixture
  private lateinit var fileFixture: TempDirTestFixture

  override lateinit var testRoot: VirtualFile

  override lateinit var gradleJvm: String

  override fun setUp() {
    listenerLeakTracker = ESListenerLeakTracker()
    listenerLeakTracker.setUp()

    reloadLeakTracker = OperationLeakTracker { getGradleProjectReloadOperation(it) }
    reloadLeakTracker.setUp()

    testDisposable = Disposer.newDisposable()

    sdkFixture = GradleTestFixtureFactory.getFixtureFactory().createGradleJvmTestFixture(gradleVersion)
    sdkFixture.setUp()
    gradleJvm = sdkFixture.getSdk().name

    fileFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    fileFixture.setUp()
    runBlocking {
      writeAction {
        testRoot = fileFixture.findOrCreateDir(className)
          .findOrCreateDirectory(methodName)
          .findOrCreateDirectory(gradleVersion.version)
      }
    }
  }

  override fun tearDown() {
    runAll(
      { fileFixture.tearDown() },
      { sdkFixture.tearDown() },
      { Disposer.dispose(testDisposable) },
      { reloadLeakTracker.tearDown() },
      { listenerLeakTracker.tearDown() }
    )
  }

  override suspend fun openProject(relativePath: String, wait: Boolean): Project {
    val projectRoot = testRoot.getDirectory(relativePath)
    return closeOpenedProjectsIfFailAsync {
      awaitAnyGradleProjectReload(wait = wait) {
        openProjectAsync(projectRoot, UnlinkedProjectStartupActivity())
      }
    }
  }

  override suspend fun linkProject(project: Project, relativePath: String) {
    val projectRoot = testRoot.getDirectory(relativePath)
    awaitAnyGradleProjectReload(wait = true) {
      linkAndRefreshGradleProject(projectRoot.path, project)
    }
  }

  override suspend fun reloadProject(project: Project, relativePath: String, configure: ImportSpecBuilder.() -> Unit) {
    awaitAnyGradleProjectReload(wait = true) {
      ExternalSystemUtil.refreshProject(
        testRoot.getDirectory(relativePath).path,
        ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
          .apply(configure)
      )
    }
  }

  override suspend fun <R> awaitAnyGradleProjectReload(wait: Boolean, action: suspend () -> R): R {
    if (!wait) {
      return action()
    }
    return reloadLeakTracker.withAllowedOperationAsync(1) {
      org.jetbrains.plugins.gradle.testFramework.util.awaitAnyGradleProjectReload {
        action()
      }
    }
  }

  override fun assertNotificationIsVisible(project: Project, isNotificationVisible: Boolean) {
    val notificationAware = AutoImportProjectNotificationAware.getInstance(project)
    Assertions.assertEquals(isNotificationVisible, notificationAware.isNotificationVisible()) {
      notificationAware.getProjectsWithNotification().toString()
    }
  }
}