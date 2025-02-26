// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectNotificationAware
import com.intellij.openapi.externalSystem.autolink.UnlinkedProjectStartupActivity
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.openProjectAsync
import com.intellij.testFramework.utils.vfs.getDirectory
import org.jetbrains.plugins.gradle.service.project.open.linkAndSyncGradleProject
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.tracker.OperationLeakTracker
import org.jetbrains.plugins.gradle.testFramework.util.awaitGradleOpenProjectConfiguration
import org.jetbrains.plugins.gradle.testFramework.util.awaitGradleProjectConfiguration
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.getGradleProjectReloadOperation
import org.junit.jupiter.api.Assertions

class GradleTestFixtureImpl(
  private val testRoot: VirtualFile,
) : GradleTestFixture {

  private lateinit var reloadLeakTracker: OperationLeakTracker

  override fun setUp() {
    reloadLeakTracker = OperationLeakTracker { getGradleProjectReloadOperation(it) }
    reloadLeakTracker.setUp()
  }

  override fun tearDown() {
    reloadLeakTracker.tearDown()
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