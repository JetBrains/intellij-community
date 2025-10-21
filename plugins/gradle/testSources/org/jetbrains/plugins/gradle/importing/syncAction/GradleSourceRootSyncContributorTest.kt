// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.platform.testFramework.assertion.listenerAssertion.ListenerAssertion
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.platform.externalSystem.testFramework.utils.module.assertNoSourceRoots
import com.intellij.platform.externalSystem.testFramework.utils.module.assertSourceRoots
import com.intellij.platform.testFramework.assertion.moduleAssertion.ContentRootAssertions.assertContentRoots
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.junit.Test

class GradleSourceRootSyncContributorTest : GradlePhasedSyncTestCase() {

  @Test
  fun `test source root creation in the multi-module Gradle project`() {

    val projectRoot = myProjectRoot.toNioPath()

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenSyncPhaseCompleted(GradleSyncPhase.SOURCE_SET_MODEL_PHASE, disposable) {
        contentRootContributorAssertion.trace {
          assertModules(myProject, "project", "project.main", "project.test")

          assertContentRoots(myProject, "project", projectRoot)
          assertNoSourceRoots(myProject, "project")

          assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
          assertSourceRoots(myProject, "project.main") {
            sourceRoots(ExternalSystemSourceType.SOURCE, projectRoot.resolve("src/main/java"))
            sourceRoots(ExternalSystemSourceType.RESOURCE, projectRoot.resolve("src/main/resources"))
          }

          assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))
          assertSourceRoots(myProject, "project.test") {
            sourceRoots(ExternalSystemSourceType.TEST, projectRoot.resolve("src/test/java"))
            sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectRoot.resolve("src/test/resources"))
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

      assertModules(myProject, "project", "project.main", "project.test")

      assertContentRoots(myProject, "project", projectRoot)
      assertNoSourceRoots(myProject, "project")

      assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
      assertSourceRoots(myProject, "project.main") {
        sourceRoots(ExternalSystemSourceType.SOURCE, projectRoot.resolve("src/main/java"))
        sourceRoots(ExternalSystemSourceType.RESOURCE, projectRoot.resolve("src/main/resources"))
      }

      assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))
      assertSourceRoots(myProject, "project.test") {
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

      whenSyncPhaseCompleted(GradleSyncPhase.SOURCE_SET_MODEL_PHASE, disposable) {
        contentRootContributorAssertion.trace {
          assertModules(
            myProject,
            "project", "project.main", "project.test",
            "project.module", "project.module.main", "project.module.test"
          )

          assertContentRoots(myProject, "project", projectRoot)
          assertNoSourceRoots(myProject, "project")

          assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
          assertSourceRoots(myProject, "project.main") {
            sourceRoots(ExternalSystemSourceType.SOURCE, projectRoot.resolve("src/main/java"))
            sourceRoots(ExternalSystemSourceType.RESOURCE, projectRoot.resolve("src/main/resources"))
          }

          assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))
          assertSourceRoots(myProject, "project.test") {
            sourceRoots(ExternalSystemSourceType.TEST, projectRoot.resolve("src/test/java"))
            sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectRoot.resolve("src/test/resources"))
          }

          assertContentRoots(myProject, "project.module", projectRoot.resolve("module"))
          assertNoSourceRoots(myProject, "project.module")

          assertContentRoots(myProject, "project.module.main", projectRoot.resolve("module/src/main"))
          assertSourceRoots(myProject, "project.module.main") {
            sourceRoots(ExternalSystemSourceType.SOURCE, projectRoot.resolve("module/src/main/java"))
            sourceRoots(ExternalSystemSourceType.RESOURCE, projectRoot.resolve("module/src/main/resources"))
          }

          assertContentRoots(myProject, "project.module.test", projectRoot.resolve("module/src/test"))
          assertSourceRoots(myProject, "project.module.test") {
            sourceRoots(ExternalSystemSourceType.TEST, projectRoot.resolve("module/src/test/java"))
            sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectRoot.resolve("module/src/test/resources"))
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
        myProject,
        "project", "project.main", "project.test",
        "project.module", "project.module.main", "project.module.test"
      )

      assertContentRoots(myProject, "project", projectRoot)
      assertNoSourceRoots(myProject, "project")

      assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
      assertSourceRoots(myProject, "project.main") {
        sourceRoots(ExternalSystemSourceType.SOURCE, projectRoot.resolve("src/main/java"))
        sourceRoots(ExternalSystemSourceType.RESOURCE, projectRoot.resolve("src/main/resources"))
      }

      assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))
      assertSourceRoots(myProject, "project.test") {
        sourceRoots(ExternalSystemSourceType.TEST, projectRoot.resolve("src/test/java"))
        sourceRoots(ExternalSystemSourceType.TEST_RESOURCE, projectRoot.resolve("src/test/resources"))
      }

      assertContentRoots(myProject, "project.module", projectRoot.resolve("module"))
      assertNoSourceRoots(myProject, "project.module")

      assertContentRoots(myProject, "project.module.main", projectRoot.resolve("module/src/main"))
      assertSourceRoots(myProject, "project.module.main") {
        sourceRoots(ExternalSystemSourceType.SOURCE, projectRoot.resolve("module/src/main/java"))
        sourceRoots(ExternalSystemSourceType.RESOURCE, projectRoot.resolve("module/src/main/resources"))
      }

      assertContentRoots(myProject, "project.module.test", projectRoot.resolve("module/src/test"))
      assertSourceRoots(myProject, "project.module.test") {
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