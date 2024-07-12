// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.testFramework.utils.module.assertContentRoots
import com.intellij.testFramework.utils.module.assertModules
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
            assertContentRoots(virtualFileUrlManager, storage, "project.main", projectRoot.resolve("src/main"))
            assertContentRoots(virtualFileUrlManager, storage, "project.test", projectRoot.resolve("src/test"))
          }
        }
      }

      createSettingsFile {
        setProjectName("project")
      }
      createBuildFile {
        withJavaPlugin()
      }

      importProject()

      assertModules(project, "project", "project.main", "project.test")
      assertContentRoots(project, "project", projectRoot)
      assertContentRoots(project, "project.main", projectRoot.resolve("src/main"))
      assertContentRoots(project, "project.test", projectRoot.resolve("src/test"))

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
            assertContentRoots(virtualFileUrlManager, storage, "project.main", projectRoot.resolve("src/main"))
            assertContentRoots(virtualFileUrlManager, storage, "project.test", projectRoot.resolve("src/test"))
            assertContentRoots(virtualFileUrlManager, storage, "project.module", projectRoot.resolve("module"))
            assertContentRoots(virtualFileUrlManager, storage, "project.module.main", projectRoot.resolve("module/src/main"))
            assertContentRoots(virtualFileUrlManager, storage, "project.module.test", projectRoot.resolve("module/src/test"))
          }
        }
      }

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
      assertContentRoots(project, "project.main", projectRoot.resolve("src/main"))
      assertContentRoots(project, "project.test", projectRoot.resolve("src/test"))
      assertContentRoots(project, "project.module", projectRoot.resolve("module"))
      assertContentRoots(project, "project.module.main", projectRoot.resolve("module/src/main"))
      assertContentRoots(project, "project.module.test", projectRoot.resolve("module/src/test"))

      contentRootContributorAssertion.assertListenerFailures()
      contentRootContributorAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once."
      }
    }
  }
}