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
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.openProjectAsync
import com.intellij.testFramework.utils.vfs.getDirectory
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.jps.model.java.JdkVersionDetector.JdkVersionInfo
import org.jetbrains.plugins.gradle.service.project.open.linkAndSyncGradleProject
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.tracker.OperationLeakTracker
import org.jetbrains.plugins.gradle.testFramework.util.awaitGradleOpenProjectConfiguration
import org.jetbrains.plugins.gradle.testFramework.util.awaitGradleProjectConfiguration
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.getGradleProjectReloadOperation
import org.junit.jupiter.api.Assertions

class GradleTestFixtureImpl(
  private val className: String,
  private val methodName: String,
  override val gradleVersion: GradleVersion,
  private val javaVersionRestriction: JavaVersionRestriction
) : GradleTestFixture {

  private lateinit var reloadLeakTracker: OperationLeakTracker

  private lateinit var testDisposable: Disposable

  private lateinit var gradleJvmFixture: GradleJvmTestFixture
  private lateinit var fileFixture: TempDirTestFixture

  override lateinit var testRoot: VirtualFile

  override lateinit var gradleJvm: String

  override lateinit var gradleJvmInfo: JdkVersionInfo

  override fun setUp() {
    reloadLeakTracker = OperationLeakTracker { getGradleProjectReloadOperation(it) }
    reloadLeakTracker.setUp()

    testDisposable = Disposer.newDisposable()

    gradleJvmFixture = GradleJvmTestFixture(gradleVersion, javaVersionRestriction)
    gradleJvmFixture.setUp()
    gradleJvmFixture.installProjectSettingsConfigurator()
    gradleJvm = gradleJvmFixture.gradleJvm
    gradleJvmInfo = gradleJvmFixture.gradleJvmInfo

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
      { gradleJvmFixture.tearDown() },
      { Disposer.dispose(testDisposable) },
      { reloadLeakTracker.tearDown() }
    )
  }

  override suspend fun openProject(relativePath: String, numProjectSyncs: Int): Project {
    val projectRoot = testRoot.getDirectory(relativePath)
    return awaitOpenProjectConfiguration(numProjectSyncs) {
      openProjectAsync(projectRoot, UnlinkedProjectStartupActivity())
    }
  }

  override suspend fun linkProject(project: Project, relativePath: String) {
    val projectRoot = testRoot.getDirectory(relativePath)
    awaitProjectConfiguration(project) {
      linkAndSyncGradleProject(project, projectRoot)
    }
  }

  override suspend fun reloadProject(project: Project, relativePath: String, configure: ImportSpecBuilder.() -> Unit) {
    awaitProjectConfiguration(project) {
      ExternalSystemUtil.refreshProject(
        testRoot.getDirectory(relativePath).path,
        ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
          .apply(configure)
      )
    }
  }

  override suspend fun awaitOpenProjectConfiguration(numProjectSyncs: Int, openProject: suspend () -> Project): Project {
    return closeOpenedProjectsIfFailAsync {
      reloadLeakTracker.withAllowedOperationAsync(numProjectSyncs) {
        awaitGradleOpenProjectConfiguration(openProject)
      }
    }
  }

  override suspend fun <R> awaitProjectConfiguration(project: Project, numProjectSyncs: Int, action: suspend () -> R): R {
    return reloadLeakTracker.withAllowedOperationAsync(numProjectSyncs) {
      awaitGradleProjectConfiguration(project, action)
    }
  }

  override fun assertNotificationIsVisible(project: Project, isNotificationVisible: Boolean) {
    val notificationAware = AutoImportProjectNotificationAware.getInstance(project)
    Assertions.assertEquals(isNotificationVisible, notificationAware.isNotificationVisible()) {
      notificationAware.getProjectsWithNotification().toString()
    }
  }
}