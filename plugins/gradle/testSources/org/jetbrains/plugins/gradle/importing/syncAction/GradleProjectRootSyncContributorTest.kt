// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.openapi.externalSystem.util.ListenerAssertion
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.getResolvedPath
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.use
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.moduleAssertion.ContentRootAssertions.assertContentRoots
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import org.jetbrains.plugins.gradle.service.project.wizard.util.generateGradleWrapper
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.junit.Test

class GradleProjectRootSyncContributorTest : GradlePhasedSyncTestCase() {

  @Test
  fun `test project root creation in the simple Gradle project`() {
    val projectRoot = projectRoot.toNioPath()
    val linkedProjectRoot = projectRoot.getResolvedPath("../linked-project")
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    Disposer.newDisposable().use { disposable ->

      val projectRootContributorAssertion = ListenerAssertion()

      whenResolveProjectInfoStarted(disposable) { _, storage ->
        projectRootContributorAssertion.trace {
          assertModules(storage, "project")
          assertContentRoots(virtualFileUrlManager, storage, "project", projectRoot)
        }
      }

      createSettingsFile {
        setProjectName("project")
      }
      createBuildFile {
        withJavaPlugin()
      }

      val settings = GradleSettings.getInstance(project)
      val projectSettings = GradleProjectSettings(projectRoot.toCanonicalPath())
      settings.linkProject(projectSettings)

      ExternalSystemUtil.refreshProject(projectRoot.toCanonicalPath(), createImportSpec())

      assertModules(project, "project", "project.main", "project.test")
      assertContentRoots(project, "project", projectRoot)
      assertContentRoots(project, "project.main", projectRoot.resolve("src/main"))
      assertContentRoots(project, "project.test", projectRoot.resolve("src/test"))

      projectRootContributorAssertion.assertListenerFailures()
      projectRootContributorAssertion.assertListenerState(1) {
        "The project info resolution should be started only once."
      }
    }

    Disposer.newDisposable().use { disposable ->

      val projectRootContributorAssertion = ListenerAssertion()

      whenResolveProjectInfoStarted(disposable) { _, storage ->
        projectRootContributorAssertion.trace {
          assertModules(storage, "project", "project.main", "project.test", "linked-project")
          assertContentRoots(virtualFileUrlManager, storage, "project", projectRoot)
          assertContentRoots(virtualFileUrlManager, storage, "project.main", projectRoot.resolve("src/main"))
          assertContentRoots(virtualFileUrlManager, storage, "project.test", projectRoot.resolve("src/test"))
          assertContentRoots(virtualFileUrlManager, storage, "linked-project", linkedProjectRoot)
        }
      }

      createSettingsFile {
        setProjectName("project")
      }
      createBuildFile {
        withJavaPlugin()
      }
      createSettingsFile("../linked-project") {
        setProjectName("linked-project")
      }
      createBuildFile("../linked-project") {
        withJavaPlugin()
      }
      generateGradleWrapper(linkedProjectRoot, currentGradleVersion)

      val settings = GradleSettings.getInstance(project)
      val projectSettings = GradleProjectSettings(linkedProjectRoot.toCanonicalPath())
      settings.linkProject(projectSettings)

      ExternalSystemUtil.refreshProject(linkedProjectRoot.toCanonicalPath(), createImportSpec())

      assertModules(
        project,
        "project", "project.main", "project.test",
        "linked-project", "linked-project.main", "linked-project.test"
      )
      assertContentRoots(project, "project", projectRoot)
      assertContentRoots(project, "project.main", projectRoot.resolve("src/main"))
      assertContentRoots(project, "project.test", projectRoot.resolve("src/test"))
      assertContentRoots(project, "linked-project", linkedProjectRoot)
      assertContentRoots(project, "linked-project.main", linkedProjectRoot.resolve("src/main"))
      assertContentRoots(project, "linked-project.test", linkedProjectRoot.resolve("src/test"))

      projectRootContributorAssertion.assertListenerFailures()
      projectRootContributorAssertion.assertListenerState(1) {
        "The project info resolution should be started only once."
      }
    }
  }
}