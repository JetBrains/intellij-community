// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.platform.testFramework.assertion.listenerAssertion.ListenerAssertion
import com.intellij.platform.testFramework.assertion.moduleAssertion.ContentRootAssertions.assertContentRoots
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.junit.Test
import org.junit.jupiter.api.Assertions
import java.util.concurrent.atomic.AtomicBoolean

class GradleContentRootSyncContributorTest : GradlePhasedSyncTestCase() {

  @Test
  fun `test content root creation in the multi-module Gradle project`() {

    val projectRoot = myProjectRoot.toNioPath()

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenSyncPhaseCompleted(GradleSyncPhase.PROJECT_MODEL_PHASE, disposable) {
        contentRootContributorAssertion.trace {
          assertModules(myProject, "project")
          assertContentRoots(myProject, "project", projectRoot)
        }
      }

      createSettingsFile {
        setProjectName("project")
      }
      createBuildFile {
        withJavaPlugin()
      }

      importProject()

      assertModules(myProject, "project", "project.main", "project.test")
      assertContentRoots(myProject, "project", projectRoot)
      assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
      assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))

      contentRootContributorAssertion.assertListenerFailures()
      contentRootContributorAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once."
      }
    }

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenSyncPhaseCompleted(GradleSyncPhase.PROJECT_MODEL_PHASE, disposable) {
        contentRootContributorAssertion.trace {
          assertModules(myProject, "project", "project.main", "project.test", "project.module")
          assertContentRoots(myProject, "project", projectRoot)
          assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
          assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))
          assertContentRoots(myProject, "project.module", projectRoot.resolve("module"))
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
        myProject,
        "project", "project.main", "project.test",
        "project.module", "project.module.main", "project.module.test"
      )
      assertContentRoots(myProject, "project", projectRoot)
      assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
      assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))
      assertContentRoots(myProject, "project.module", projectRoot.resolve("module"))
      assertContentRoots(myProject, "project.module.main", projectRoot.resolve("module/src/main"))
      assertContentRoots(myProject, "project.module.test", projectRoot.resolve("module/src/test"))

      contentRootContributorAssertion.assertListenerFailures()
      contentRootContributorAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once."
      }
    }
  }

  @Test
  fun `test content root creation in the Gradle project with included build`() {

    val projectRoot = myProjectRoot.toNioPath()

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenSyncPhaseCompleted(GradleSyncPhase.PROJECT_MODEL_PHASE, disposable) {
        contentRootContributorAssertion.trace {
          assertModules(myProject, "project", "includedProject1")
          assertContentRoots(myProject, "project", projectRoot)
          assertContentRoots(myProject, "includedProject1", projectRoot.resolve("../includedProject1"))
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
        myProject,
        "project", "project.main", "project.test",
        "includedProject1", "includedProject1.main", "includedProject1.test",
      )
      assertContentRoots(myProject, "project", projectRoot)
      assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
      assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))
      assertContentRoots(myProject, "includedProject1", projectRoot.resolve("../includedProject1"))
      assertContentRoots(myProject, "includedProject1.main", projectRoot.resolve("../includedProject1/src/main"))
      assertContentRoots(myProject, "includedProject1.test", projectRoot.resolve("../includedProject1/src/test"))

      contentRootContributorAssertion.assertListenerFailures()
      contentRootContributorAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once"
      }
    }

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenSyncPhaseCompleted(GradleSyncPhase.PROJECT_MODEL_PHASE, disposable) {
        contentRootContributorAssertion.trace {
          assertModules(
            myProject,
            "project", "project.main", "project.test",
            "includedProject1", "includedProject1.main", "includedProject1.test",
            "includedProject2"
          )
          assertContentRoots(myProject, "project", projectRoot)
          assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
          assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))
          assertContentRoots(myProject, "includedProject1", projectRoot.resolve("../includedProject1"))
          assertContentRoots(myProject, "includedProject1.main", projectRoot.resolve("../includedProject1/src/main"))
          assertContentRoots(myProject, "includedProject1.test", projectRoot.resolve("../includedProject1/src/test"))
          assertContentRoots(myProject, "includedProject2", projectRoot.resolve("../includedProject2"))
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
        myProject,
        "project", "project.main", "project.test",
        "includedProject1", "includedProject1.main", "includedProject1.test",
        "includedProject2", "includedProject2.main", "includedProject2.test",
      )
      assertContentRoots(myProject, "project", projectRoot)
      assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
      assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))
      assertContentRoots(myProject, "includedProject1", projectRoot.resolve("../includedProject1"))
      assertContentRoots(myProject, "includedProject1.main", projectRoot.resolve("../includedProject1/src/main"))
      assertContentRoots(myProject, "includedProject1.test", projectRoot.resolve("../includedProject1/src/test"))
      assertContentRoots(myProject, "includedProject2", projectRoot.resolve("../includedProject2"))
      assertContentRoots(myProject, "includedProject2.main", projectRoot.resolve("../includedProject2/src/main"))
      assertContentRoots(myProject, "includedProject2.test", projectRoot.resolve("../includedProject2/src/test"))

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

    val projectRoot = myProjectRoot.toNioPath()

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      val isBuildSrcShouldBeResolved = AtomicBoolean(!isBuildSrcResolvedOnSecondCall)

      whenSyncPhaseCompleted(GradleSyncPhase.PROJECT_MODEL_PHASE, disposable) {
        contentRootContributorAssertion.trace {
          when {
            !isBuildSrcShouldBeResolved.getAndSet(true) -> {
              assertModules(myProject, "project")
              assertContentRoots(myProject, "project", projectRoot)
            }
            isBuildSrcResolvedOnSecondCall -> {
              assertModules(myProject, "project", "project.main", "project.test", "project.buildSrc")
              assertContentRoots(myProject, "project", projectRoot)
              assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
              assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))
              assertContentRoots(myProject, "project.buildSrc", projectRoot.resolve("buildSrc"))
            }
            else -> {
              assertModules(myProject, "project", "project.buildSrc")
              assertContentRoots(myProject, "project", projectRoot)
              assertContentRoots(myProject, "project.buildSrc", projectRoot.resolve("buildSrc"))
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
        myProject,
        "project", "project.main", "project.test",
        "project.buildSrc", "project.buildSrc.main", "project.buildSrc.test"
      )
      assertContentRoots(myProject, "project", projectRoot)
      assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
      assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))
      assertContentRoots(myProject, "project.buildSrc", projectRoot.resolve("buildSrc"))
      assertContentRoots(myProject, "project.buildSrc.main", projectRoot.resolve("buildSrc/src/main"))
      assertContentRoots(myProject, "project.buildSrc.test", projectRoot.resolve("buildSrc/src/test"))

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

      whenSyncPhaseCompleted(GradleSyncPhase.PROJECT_MODEL_PHASE, disposable) {
        contentRootContributorAssertion.trace {
          assertModules(
            myProject,
            "project", "project.main", "project.test",
            "project.buildSrc", "project.buildSrc.main", "project.buildSrc.test"
          )
          assertContentRoots(myProject, "project", projectRoot)
          assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
          assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))
          assertContentRoots(myProject, "project.buildSrc", projectRoot.resolve("buildSrc"))
          assertContentRoots(myProject, "project.buildSrc.main", projectRoot.resolve("buildSrc/src/main"))
          assertContentRoots(myProject, "project.buildSrc.test", projectRoot.resolve("buildSrc/src/test"))
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
      assertContentRoots(myProject, "project", projectRoot)
      assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
      assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))
      assertContentRoots(myProject, "project.buildSrc", projectRoot.resolve("buildSrc"))
      assertContentRoots(myProject, "project.buildSrc.main", projectRoot.resolve("buildSrc/src/main"))
      assertContentRoots(myProject, "project.buildSrc.test", projectRoot.resolve("buildSrc/src/test"))

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

    val projectRoot = myProjectRoot.toNioPath()
    val externalProjectRoot = myTestDir.resolve("external/project/root")

    Assertions.assertFalse(projectRoot.startsWith(externalProjectRoot)) {
      """
        |External project root shouldn't be under the project root.
        |Project root: $projectRoot
        |External project root: $externalProjectRoot
      """.trimMargin()
    }

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenSyncPhaseCompleted(GradleSyncPhase.SOURCE_SET_MODEL_PHASE, disposable) {
        contentRootContributorAssertion.trace {
          assertModules(myProject, "project", "project.main", "project.test")
          assertContentRoots(myProject, "project", projectRoot)
          assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"), externalProjectRoot.resolve("src/main"))
          assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))
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

      assertModules(myProject, "project", "project.main", "project.test")
      assertContentRoots(myProject, "project", projectRoot)
      assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"), externalProjectRoot.resolve("src/main"))
      assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))

      contentRootContributorAssertion.assertListenerFailures()
      contentRootContributorAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once"
      }
    }
  }

  @Test
  fun `test content root configuration with single source root`() {

    val projectRoot = myProjectRoot.toNioPath()

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenSyncPhaseCompleted(GradleSyncPhase.SOURCE_SET_MODEL_PHASE, disposable) {
        contentRootContributorAssertion.trace {
          assertModules(myProject, "project", "project.main", "project.test")
          assertContentRoots(myProject, "project", projectRoot)
          assertContentRoots(myProject, "project.main", projectRoot.resolve("src"))
          assertContentRoots(myProject, "project.test")
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

      assertModules(myProject, "project", "project.main", "project.test")
      assertContentRoots(myProject, "project", projectRoot)
      assertContentRoots(myProject, "project.main", projectRoot.resolve("src"))
      assertContentRoots(myProject, "project.test")

      contentRootContributorAssertion.assertListenerFailures()
      contentRootContributorAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once"
      }
    }
  }
}