// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.gradle.toolingExtension.modelAction.GradleModelFetchPhase
import com.intellij.platform.testFramework.assertion.listenerAssertion.ListenerAssertion
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.moduleAssertion.ContentRootAssertions.assertContentRoots
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.junit.Test
import org.junit.jupiter.api.Assertions
import java.util.concurrent.atomic.AtomicBoolean

class GradleContentRootSyncContributorTest : GradlePhasedSyncTestCase() {

  @Test
  fun `test content root creation in the multi-module Gradle project`() {

    val projectRoot = projectRoot.toNioPath()
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenPhaseCompleted(disposable) { _, storage, phase ->
        if (phase == GradleModelFetchPhase.PROJECT_MODEL_PHASE) {
          contentRootContributorAssertion.trace {
            assertModules(storage, "project")
            assertContentRoots(virtualFileUrlManager, storage, "project", projectRoot)
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
        if (phase == GradleModelFetchPhase.PROJECT_MODEL_PHASE) {
          contentRootContributorAssertion.trace {
            assertModules(storage, "project", "project.main", "project.test", "project.module")
            assertContentRoots(virtualFileUrlManager, storage, "project", projectRoot)
            assertContentRoots(virtualFileUrlManager, storage, "project.main", projectRoot.resolve("src/main"))
            assertContentRoots(virtualFileUrlManager, storage, "project.test", projectRoot.resolve("src/test"))
            assertContentRoots(virtualFileUrlManager, storage, "project.module", projectRoot.resolve("module"))
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

  @Test
  fun `test content root creation in the Gradle project with included build`() {

    val projectRoot = projectRoot.toNioPath()
    val virtualFileUrlManager = myProject.workspaceModel.getVirtualFileUrlManager()

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenPhaseCompleted(disposable) { _, storage, phase ->
        if (phase == GradleModelFetchPhase.PROJECT_MODEL_PHASE) {
          contentRootContributorAssertion.trace {
            assertModules(storage, "project", "includedProject1")
            assertContentRoots(virtualFileUrlManager, storage, "project", projectRoot)
            assertContentRoots(virtualFileUrlManager, storage, "includedProject1", projectRoot.resolve("../includedProject1"))
          }
        }
      }

      createSettingsFile {
        setProjectName("project")
        includeBuild("../includedProject1")
      }
      createBuildFile {
        withJavaPlugin()
      }
      createSettingsFile("../includedProject1") {
        setProjectName("includedProject1")
      }
      createBuildFile("../includedProject1") {
        withJavaPlugin()
      }

      importProject()

      assertModules(
        project,
        "project", "project.main", "project.test",
        "includedProject1", "includedProject1.main", "includedProject1.test",
      )
      assertContentRoots(project, "project", projectRoot)
      assertContentRoots(project, "project.main", projectRoot.resolve("src/main"))
      assertContentRoots(project, "project.test", projectRoot.resolve("src/test"))
      assertContentRoots(project, "includedProject1", projectRoot.resolve("../includedProject1"))
      assertContentRoots(project, "includedProject1.main", projectRoot.resolve("../includedProject1/src/main"))
      assertContentRoots(project, "includedProject1.test", projectRoot.resolve("../includedProject1/src/test"))

      contentRootContributorAssertion.assertListenerFailures()
      contentRootContributorAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once"
      }
    }

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenPhaseCompleted(disposable) { _, storage, phase ->
        if (phase == GradleModelFetchPhase.PROJECT_MODEL_PHASE) {
          contentRootContributorAssertion.trace {
            assertModules(
              storage,
              "project", "project.main", "project.test",
              "includedProject1", "includedProject1.main", "includedProject1.test",
              "includedProject2"
            )
            assertContentRoots(virtualFileUrlManager, storage, "project", projectRoot)
            assertContentRoots(virtualFileUrlManager, storage, "project.main", projectRoot.resolve("src/main"))
            assertContentRoots(virtualFileUrlManager, storage, "project.test", projectRoot.resolve("src/test"))
            assertContentRoots(virtualFileUrlManager, storage, "includedProject1", projectRoot.resolve("../includedProject1"))
            assertContentRoots(virtualFileUrlManager, storage, "includedProject1.main", projectRoot.resolve("../includedProject1/src/main"))
            assertContentRoots(virtualFileUrlManager, storage, "includedProject1.test", projectRoot.resolve("../includedProject1/src/test"))
            assertContentRoots(virtualFileUrlManager, storage, "includedProject2", projectRoot.resolve("../includedProject2"))
          }
        }
      }

      createSettingsFile {
        setProjectName("project")
        includeBuild("../includedProject1")
        includeBuild("../includedProject2")
      }
      createBuildFile {
        withJavaPlugin()
      }
      createSettingsFile("../includedProject1") {
        setProjectName("includedProject1")
      }
      createBuildFile("../includedProject1") {
        withJavaPlugin()
      }
      createSettingsFile("../includedProject2") {
        setProjectName("includedProject2")
      }
      createBuildFile("../includedProject2") {
        withJavaPlugin()
      }

      importProject()

      assertModules(
        project,
        "project", "project.main", "project.test",
        "includedProject1", "includedProject1.main", "includedProject1.test",
        "includedProject2", "includedProject2.main", "includedProject2.test",
      )
      assertContentRoots(project, "project", projectRoot)
      assertContentRoots(project, "project.main", projectRoot.resolve("src/main"))
      assertContentRoots(project, "project.test", projectRoot.resolve("src/test"))
      assertContentRoots(project, "includedProject1", projectRoot.resolve("../includedProject1"))
      assertContentRoots(project, "includedProject1.main", projectRoot.resolve("../includedProject1/src/main"))
      assertContentRoots(project, "includedProject1.test", projectRoot.resolve("../includedProject1/src/test"))
      assertContentRoots(project, "includedProject2", projectRoot.resolve("../includedProject2"))
      assertContentRoots(project, "includedProject2.main", projectRoot.resolve("../includedProject2/src/main"))
      assertContentRoots(project, "includedProject2.test", projectRoot.resolve("../includedProject2/src/test"))

      contentRootContributorAssertion.assertListenerFailures()
      contentRootContributorAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once"
      }
    }
  }

  @Test
  fun `test content root configuration in the Gradle project with buildSrc`() {

    // The buildSrc should be resolved on the second Gradle call for Gradle versions order than 8.0.
    // However, IDEA should keep the old buildSrc modules in the next re-syncs.
    val isBuildSrcResolvedOnSecondCall = isGradleOlderThan("8.0")

    val projectRoot = projectRoot.toNioPath()
    val virtualFileUrlManager = myProject.workspaceModel.getVirtualFileUrlManager()

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      val isBuildSrcShouldBeResolved = AtomicBoolean(!isBuildSrcResolvedOnSecondCall)

      whenPhaseCompleted(disposable) { _, storage, phase ->
        if (phase == GradleModelFetchPhase.PROJECT_MODEL_PHASE) {
          contentRootContributorAssertion.trace {
            when {
              !isBuildSrcShouldBeResolved.getAndSet(true) -> {
                assertModules(storage, "project")
                assertContentRoots(virtualFileUrlManager, storage, "project", projectRoot)
              }
              isBuildSrcResolvedOnSecondCall -> {
                assertModules(storage, "project", "project.main", "project.test", "project.buildSrc")
                assertContentRoots(virtualFileUrlManager, storage, "project", projectRoot)
                assertContentRoots(virtualFileUrlManager, storage, "project.main", projectRoot.resolve("src/main"))
                assertContentRoots(virtualFileUrlManager, storage, "project.test", projectRoot.resolve("src/test"))
                assertContentRoots(virtualFileUrlManager, storage, "project.buildSrc", projectRoot.resolve("buildSrc"))
              }
              else -> {
                assertModules(storage, "project", "project.buildSrc")
                assertContentRoots(virtualFileUrlManager, storage, "project", projectRoot)
                assertContentRoots(virtualFileUrlManager, storage, "project.buildSrc", projectRoot.resolve("buildSrc"))
              }
            }
          }
        }
      }

      createSettingsFile {
        setProjectName("project")
      }
      createBuildFile("buildSrc") {
        withPlugin("groovy")
        addImplementationDependency(code("gradleApi()"))
        addImplementationDependency(code("localGroovy()"))
      }
      createBuildFile {
        withJavaPlugin()
      }

      importProject()

      assertModules(
        project,
        "project", "project.main", "project.test",
        "project.buildSrc", "project.buildSrc.main", "project.buildSrc.test"
      )
      assertContentRoots(project, "project", projectRoot)
      assertContentRoots(project, "project.main", projectRoot.resolve("src/main"))
      assertContentRoots(project, "project.test", projectRoot.resolve("src/test"))
      assertContentRoots(project, "project.buildSrc", projectRoot.resolve("buildSrc"))
      assertContentRoots(project, "project.buildSrc.main", projectRoot.resolve("buildSrc/src/main"))
      assertContentRoots(project, "project.buildSrc.test", projectRoot.resolve("buildSrc/src/test"))

      contentRootContributorAssertion.assertListenerFailures()
      if (isBuildSrcResolvedOnSecondCall) {
        contentRootContributorAssertion.assertListenerState(2) {
          "The project loaded phase should be finished twice.\n" +
          "The buildSrc should be resolved on the second Gradle call for Gradle versions order than 8.0."
        }
      }
      else {
        contentRootContributorAssertion.assertListenerState(1) {
          "The project loaded phase should be finished only once."
        }
      }
    }

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenPhaseCompleted(disposable) { _, storage, phase ->
        if (phase == GradleModelFetchPhase.PROJECT_MODEL_PHASE) {
          contentRootContributorAssertion.trace {
            assertModules(
              storage,
              "project", "project.main", "project.test",
              "project.buildSrc", "project.buildSrc.main", "project.buildSrc.test"
            )
            assertContentRoots(virtualFileUrlManager, storage, "project", projectRoot)
            assertContentRoots(virtualFileUrlManager, storage, "project.main", projectRoot.resolve("src/main"))
            assertContentRoots(virtualFileUrlManager, storage, "project.test", projectRoot.resolve("src/test"))
            assertContentRoots(virtualFileUrlManager, storage, "project.buildSrc", projectRoot.resolve("buildSrc"))
            assertContentRoots(virtualFileUrlManager, storage, "project.buildSrc.main", projectRoot.resolve("buildSrc/src/main"))
            assertContentRoots(virtualFileUrlManager, storage, "project.buildSrc.test", projectRoot.resolve("buildSrc/src/test"))
          }
        }
      }

      createSettingsFile {
        setProjectName("project")
      }
      createBuildFile("buildSrc") {
        withPlugin("groovy")
        addImplementationDependency(code("gradleApi()"))
        addImplementationDependency(code("localGroovy()"))
      }
      createBuildFile {
        withJavaPlugin()
      }

      importProject()

      assertModules(
        "project", "project.main", "project.test",
        "project.buildSrc", "project.buildSrc.main", "project.buildSrc.test"
      )
      assertContentRoots(project, "project", projectRoot)
      assertContentRoots(project, "project.main", projectRoot.resolve("src/main"))
      assertContentRoots(project, "project.test", projectRoot.resolve("src/test"))
      assertContentRoots(project, "project.buildSrc", projectRoot.resolve("buildSrc"))
      assertContentRoots(project, "project.buildSrc.main", projectRoot.resolve("buildSrc/src/main"))
      assertContentRoots(project, "project.buildSrc.test", projectRoot.resolve("buildSrc/src/test"))

      contentRootContributorAssertion.assertListenerFailures()
      if (isBuildSrcResolvedOnSecondCall) {
        contentRootContributorAssertion.assertListenerState(2) {
          "The project loaded phase should be finished twice. " +
          "The Gradle older than 8.0 cannot resolve buildSrc by the one Gradle call."
        }
      }
      else {
        contentRootContributorAssertion.assertListenerState(1) {
          "The project loaded phase should be finished only once"
        }
      }
    }
  }

  @Test
  fun `test content root configuration outside project root`() {

    val projectRoot = projectRoot.toNioPath()
    val externalProjectRoot = myTestDir.toPath().resolve("external/project/root")
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    Assertions.assertFalse(projectRoot.startsWith(externalProjectRoot)) {
      """
        |External project root shouldn't be under the project root.
        |Project root: $projectRoot
        |External project root: $externalProjectRoot
      """.trimMargin()
    }

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenPhaseCompleted(disposable) { _, storage, phase ->
        if (phase == GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE) {
          contentRootContributorAssertion.trace {
            assertModules(storage, "project", "project.main", "project.test")
            assertContentRoots(virtualFileUrlManager, storage, "project", projectRoot)
            assertContentRoots(virtualFileUrlManager, storage, "project.main", projectRoot.resolve("src/main"), externalProjectRoot.resolve("src/main"))
            assertContentRoots(virtualFileUrlManager, storage, "project.test", projectRoot.resolve("src/test"))
          }
        }
      }

      createSettingsFile {
        setProjectName("project")
      }
      createBuildFile {
        withJavaPlugin()
        addPostfix("""
          |sourceSets.main.java.srcDirs += "${externalProjectRoot.resolve("src/main/java")}"
        """.trimMargin())
      }

      importProject()

      assertModules(project, "project", "project.main", "project.test")
      assertContentRoots(project, "project", projectRoot)
      assertContentRoots(project, "project.main", projectRoot.resolve("src/main"), externalProjectRoot.resolve("src/main"))
      assertContentRoots(project, "project.test", projectRoot.resolve("src/test"))

      contentRootContributorAssertion.assertListenerFailures()
      contentRootContributorAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once"
      }
    }
  }

  @Test
  fun `test content root configuration with single source root`() {

    val projectRoot = projectRoot.toNioPath()
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenPhaseCompleted(disposable) { _, storage, phase ->
        if (phase == GradleModelFetchPhase.PROJECT_SOURCE_SET_PHASE) {
          contentRootContributorAssertion.trace {
            assertModules(storage, "project", "project.main", "project.test")
            assertContentRoots(virtualFileUrlManager, storage, "project", projectRoot)
            assertContentRoots(virtualFileUrlManager, storage, "project.main", projectRoot.resolve("src"))
            assertContentRoots(virtualFileUrlManager, storage, "project.test")
          }
        }
      }

      createSettingsFile {
        setProjectName("project")
      }
      createBuildFile {
        withJavaPlugin()
        addPostfix("""
          |sourceSets.main.java.srcDirs = ["src"]
          |sourceSets.main.resources.srcDirs = []
          |sourceSets.test.java.srcDirs = []
          |sourceSets.test.resources.srcDirs = []
        """.trimMargin())
      }

      importProject()

      assertModules(project, "project", "project.main", "project.test")
      assertContentRoots(project, "project", projectRoot)
      assertContentRoots(project, "project.main", projectRoot.resolve("src"))
      assertContentRoots(project, "project.test")

      contentRootContributorAssertion.assertListenerFailures()
      contentRootContributorAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once"
      }
    }
  }
}