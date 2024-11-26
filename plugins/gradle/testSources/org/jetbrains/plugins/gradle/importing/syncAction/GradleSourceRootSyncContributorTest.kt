// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.util.ListenerAssertion
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.externalSystem.testFramework.utils.module.assertNoSourceRoots
import com.intellij.platform.externalSystem.testFramework.utils.module.assertSourceRoots
import com.intellij.platform.testFramework.assertion.moduleAssertion.ContentRootAssertions.assertContentRoots
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.junit.Test

class GradleSourceRootSyncContributorTest : GradlePhasedSyncTestCase() {

  @Test
  fun `test source root creation in the multi-module Gradle project`() {

    val projectRoot = projectRoot.toNioPath()
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenPhaseCompleted(disposable) { _, storage, phase ->
        if (phase == GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE) {
          contentRootContributorAssertion.trace {
            assertModules(storage, "project", "project.main", "project.test")

            assertContentRoots(virtualFileUrlManager, storage, "project", projectRoot)
            assertNoSourceRoots(virtualFileUrlManager, storage, "project")

            assertContentRoots(virtualFileUrlManager, storage, "project.main", projectRoot.resolve("src/main"))
            assertSourceRoots(virtualFileUrlManager, storage, "project.main") {
              sourceRoots(ExternalSystemSourceType.SOURCE, projectRoot.resolve("src/main/java"))
              sourceRoots(ExternalSystemSourceType.RESOURCE, projectRoot.resolve("src/main/resources"))
            }

            assertContentRoots(virtualFileUrlManager, storage, "project.test", projectRoot.resolve("src/test"))
            assertSourceRoots(virtualFileUrlManager, storage, "project.test") {
              sourceRoots(ExternalSystemSourceType.TEST, projectRoot.resolve("src/test/java"))
              sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectRoot.resolve("src/test/resources"))
            }
          }
        }
      }

      createProjectSubDir("src/main/java")
      createProjectSubDir("src/main/resources")
      createProjectSubDir("src/test/java")
      createProjectSubDir("src/test/resources")
      createSettingsFile {
        setProjectName("project")
      }
      createBuildFile {
        withJavaPlugin()
      }

      importProject()

      assertModules(project, "project", "project.main", "project.test")

      assertContentRoots(project, "project", projectRoot)
      assertNoSourceRoots(project, "project")

      assertContentRoots(project, "project.main", projectRoot.resolve("src/main"))
      assertSourceRoots(project, "project.main") {
        sourceRoots(ExternalSystemSourceType.SOURCE, projectRoot.resolve("src/main/java"))
        sourceRoots(ExternalSystemSourceType.RESOURCE, projectRoot.resolve("src/main/resources"))
      }

      assertContentRoots(project, "project.test", projectRoot.resolve("src/test"))
      assertSourceRoots(project, "project.test") {
        sourceRoots(ExternalSystemSourceType.TEST, projectRoot.resolve("src/test/java"))
        sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectRoot.resolve("src/test/resources"))
      }

      contentRootContributorAssertion.assertListenerFailures()
      contentRootContributorAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once."
      }
    }

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenPhaseCompleted(disposable) { _, storage, phase ->
        if (phase == GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE) {
          contentRootContributorAssertion.trace {
            assertModules(
              storage,
              "project", "project.main", "project.test",
              "project.module", "project.module.main", "project.module.test"
            )

            assertContentRoots(virtualFileUrlManager, storage, "project", projectRoot)
            assertNoSourceRoots(virtualFileUrlManager, storage, "project")

            assertContentRoots(virtualFileUrlManager, storage, "project.main", projectRoot.resolve("src/main"))
            assertSourceRoots(virtualFileUrlManager, storage, "project.main") {
              sourceRoots(ExternalSystemSourceType.SOURCE, projectRoot.resolve("src/main/java"))
              sourceRoots(ExternalSystemSourceType.RESOURCE, projectRoot.resolve("src/main/resources"))
            }

            assertContentRoots(virtualFileUrlManager, storage, "project.test", projectRoot.resolve("src/test"))
            assertSourceRoots(virtualFileUrlManager, storage, "project.test") {
              sourceRoots(ExternalSystemSourceType.TEST, projectRoot.resolve("src/test/java"))
              sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectRoot.resolve("src/test/resources"))
            }

            assertContentRoots(virtualFileUrlManager, storage, "project.module", projectRoot.resolve("module"))
            assertNoSourceRoots(virtualFileUrlManager, storage, "project.module")

            assertContentRoots(virtualFileUrlManager, storage, "project.module.main", projectRoot.resolve("module/src/main"))
            assertSourceRoots(virtualFileUrlManager, storage, "project.module.main") {
              sourceRoots(ExternalSystemSourceType.SOURCE, projectRoot.resolve("module/src/main/java"))
              sourceRoots(ExternalSystemSourceType.RESOURCE, projectRoot.resolve("module/src/main/resources"))
            }

            assertContentRoots(virtualFileUrlManager, storage, "project.module.test", projectRoot.resolve("module/src/test"))
            assertSourceRoots(virtualFileUrlManager, storage, "project.module.test") {
              sourceRoots(ExternalSystemSourceType.TEST, projectRoot.resolve("module/src/test/java"))
              sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectRoot.resolve("module/src/test/resources"))
            }
          }
        }
      }

      createProjectSubDir("src/main/java")
      createProjectSubDir("src/main/resources")
      createProjectSubDir("src/test/java")
      createProjectSubDir("src/test/resources")
      createProjectSubDir("module/src/main/java")
      createProjectSubDir("module/src/main/resources")
      createProjectSubDir("module/src/test/java")
      createProjectSubDir("module/src/test/resources")
      createSettingsFile {
        setProjectName("project")
        include("module")
      }
      createBuildFile {
        withJavaPlugin()
      }
      createBuildFile("module") {
        withJavaPlugin()
      }

      importProject()

      assertModules(
        project,
        "project", "project.main", "project.test",
        "project.module", "project.module.main", "project.module.test"
      )

      assertContentRoots(project, "project", projectRoot)
      assertNoSourceRoots(project, "project")

      assertContentRoots(project, "project.main", projectRoot.resolve("src/main"))
      assertSourceRoots(project, "project.main") {
        sourceRoots(ExternalSystemSourceType.SOURCE, projectRoot.resolve("src/main/java"))
        sourceRoots(ExternalSystemSourceType.RESOURCE, projectRoot.resolve("src/main/resources"))
      }

      assertContentRoots(project, "project.test", projectRoot.resolve("src/test"))
      assertSourceRoots(project, "project.test") {
        sourceRoots(ExternalSystemSourceType.TEST, projectRoot.resolve("src/test/java"))
        sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectRoot.resolve("src/test/resources"))
      }

      assertContentRoots(project, "project.module", projectRoot.resolve("module"))
      assertNoSourceRoots(project, "project.module")

      assertContentRoots(project, "project.module.main", projectRoot.resolve("module/src/main"))
      assertSourceRoots(project, "project.module.main") {
        sourceRoots(ExternalSystemSourceType.SOURCE, projectRoot.resolve("module/src/main/java"))
        sourceRoots(ExternalSystemSourceType.RESOURCE, projectRoot.resolve("module/src/main/resources"))
      }

      assertContentRoots(project, "project.module.test", projectRoot.resolve("module/src/test"))
      assertSourceRoots(project, "project.module.test") {
        sourceRoots(ExternalSystemSourceType.TEST, projectRoot.resolve("module/src/test/java"))
        sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectRoot.resolve("module/src/test/resources"))
      }

      contentRootContributorAssertion.assertListenerFailures()
      contentRootContributorAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once."
      }
    }
  }
}