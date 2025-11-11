// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.getResolvedPath
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.use
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.testFramework.assertion.WorkspaceAssertions.assertEntities
import com.intellij.platform.testFramework.assertion.listenerAssertion.ListenerAssertion
import com.intellij.platform.testFramework.assertion.moduleAssertion.ContentRootAssertions.assertContentRoots
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntityId
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.testFramework.assertions.assertGradleBuildEntity
import org.jetbrains.plugins.gradle.testFramework.assertions.assertGradleModuleEntities
import org.jetbrains.plugins.gradle.testFramework.assertions.assertGradleProjectEntity
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createGradleWrapper
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.junit.Test
import org.junit.jupiter.api.Assertions
import java.util.concurrent.atomic.AtomicBoolean

class GradleContentRootSyncContributorTest : GradlePhasedSyncTestCase() {

  @Test
  fun `test workspace entities creation in the multi-module Gradle project`() {

    val projectRoot = myProjectRoot.toNioPath()

    val externalProjectId = ExternalProjectEntityId(projectRoot.toCanonicalPath())
    val rootBuildUrl = projectRoot.toVirtualFileUrl(myProject.workspaceModel.getVirtualFileUrlManager())
    val rootBuildId = GradleBuildEntityId(externalProjectId, rootBuildUrl)
    val rootProjectId = GradleProjectEntityId(rootBuildId, rootBuildUrl)

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenSyncPhaseCompleted(GradleSyncPhase.PROJECT_MODEL_PHASE, disposable) {
        contentRootContributorAssertion.trace {
          assertModules(myProject, "project")

          assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
          assertEntities<GradleBuildEntity>(myProject, rootBuildId)
          assertEntities<GradleProjectEntity>(myProject, rootProjectId)
          assertGradleModuleEntities(myProject, "project" to rootProjectId)

          assertGradleBuildEntity(myProject, rootBuildUrl, externalProjectId, listOf(rootProjectId))
          assertGradleProjectEntity(
            myProject, projectUrl = rootBuildUrl,
            buildId = rootBuildId, path = ":",
            linkedProjectId = "project", identityPath = ":",
          )

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

      assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
      assertEntities<GradleBuildEntity>(myProject, rootBuildId)
      assertEntities<GradleProjectEntity>(myProject, rootProjectId)
      assertGradleModuleEntities(myProject, "project" to rootProjectId)

      assertGradleBuildEntity(myProject, rootBuildUrl, externalProjectId, listOf(rootProjectId))
      assertGradleProjectEntity(
        myProject, projectUrl = rootBuildUrl,
        buildId = rootBuildId, path = ":",
        linkedProjectId = "project", identityPath = ":",
      )

      assertContentRoots(myProject, "project", projectRoot)
      assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
      assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))

      contentRootContributorAssertion.assertListenerFailures()
      contentRootContributorAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once."
      }
    }

    val rootSubprojectUrl = rootBuildUrl.append("module")
    val rootSubprojectId = GradleProjectEntityId(rootBuildId, rootSubprojectUrl)

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenSyncPhaseCompleted(GradleSyncPhase.PROJECT_MODEL_PHASE, disposable) {
        contentRootContributorAssertion.trace {
          assertModules(myProject, "project", "project.main", "project.test", "project.module")

          assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
          assertEntities<GradleBuildEntity>(myProject, rootBuildId)
          assertEntities<GradleProjectEntity>(myProject, rootProjectId, rootSubprojectId)

          assertGradleModuleEntities(
            myProject,
            "project" to rootProjectId,
            "project.module" to rootSubprojectId
          )

          assertGradleBuildEntity(myProject, rootBuildUrl, externalProjectId, listOf(rootProjectId, rootSubprojectId))
          assertGradleProjectEntity(
            myProject, projectUrl = rootBuildUrl,
            buildId = rootBuildId, path = ":",
            linkedProjectId = "project", identityPath = ":",
          )
          assertGradleProjectEntity(
            myProject, projectUrl = rootSubprojectUrl,
            buildId = rootBuildId, path = ":module",
            linkedProjectId = ":module", identityPath = ":module",
          )

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

      assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
      assertEntities<GradleBuildEntity>(myProject, rootBuildId)
      assertEntities<GradleProjectEntity>(myProject, rootProjectId, rootSubprojectId)
      assertGradleModuleEntities(
        myProject,
        "project" to rootProjectId,
        "project.module" to rootSubprojectId
      )

      assertGradleBuildEntity(myProject, rootBuildUrl, externalProjectId, listOf(rootProjectId, rootSubprojectId))
      assertGradleProjectEntity(
        myProject, projectUrl = rootBuildUrl,
        buildId = rootBuildId, path = ":",
        linkedProjectId = "project", identityPath = ":",
      )
      assertGradleProjectEntity(
        myProject, projectUrl = rootSubprojectUrl,
        buildId = rootBuildId, path = ":module",
        linkedProjectId = ":module", identityPath = ":module",
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
  fun `test workspace entities creation in the Gradle project with included build`() {

    val projectRoot = myProjectRoot.toNioPath()

    val externalProjectId = ExternalProjectEntityId(projectRoot.toCanonicalPath())
    val rootBuildUrl = projectRoot.toVirtualFileUrl(myProject.workspaceModel.getVirtualFileUrlManager())
    val rootBuildId = GradleBuildEntityId(externalProjectId, rootBuildUrl)
    val rootProjectId = GradleProjectEntityId(rootBuildId, rootBuildUrl)

    val firstIncludedBuildUrl = rootBuildUrl.parent!!.append("includedProject1")
    val firstIncludedBuildId = GradleBuildEntityId(externalProjectId, firstIncludedBuildUrl)
    val firstIncludedProjectId = GradleProjectEntityId(firstIncludedBuildId, firstIncludedBuildUrl)

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenSyncPhaseCompleted(GradleSyncPhase.PROJECT_MODEL_PHASE, disposable) {
        contentRootContributorAssertion.trace {
          assertModules(myProject, "project", "includedProject1")

          assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
          assertEntities<GradleBuildEntity>(myProject, rootBuildId, firstIncludedBuildId)
          assertEntities<GradleProjectEntity>(myProject, rootProjectId, firstIncludedProjectId)
          assertGradleModuleEntities(
            myProject,
            "project" to rootProjectId,
            "includedProject1" to firstIncludedProjectId
          )

          assertGradleBuildEntity(myProject, rootBuildUrl, externalProjectId, listOf(rootProjectId))
          assertGradleProjectEntity(
            myProject, projectUrl = rootBuildUrl,
            buildId = rootBuildId, path = ":",
            linkedProjectId = "project", identityPath = ":",
          )

          assertGradleBuildEntity(myProject, firstIncludedBuildUrl, externalProjectId, listOf(firstIncludedProjectId))
          assertGradleProjectEntity(
            myProject, projectUrl = firstIncludedBuildUrl,
            buildId = firstIncludedBuildId, path = ":",
            linkedProjectId = ":includedProject1", identityPath = ":includedProject1",
          )

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

      assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
      assertEntities<GradleBuildEntity>(myProject, rootBuildId, firstIncludedBuildId)
      assertEntities<GradleProjectEntity>(myProject, rootProjectId, firstIncludedProjectId)
      assertGradleModuleEntities(
        myProject,
        "project" to rootProjectId,
        "includedProject1" to firstIncludedProjectId
      )

      assertGradleBuildEntity(myProject, rootBuildUrl, externalProjectId, listOf(rootProjectId))
      assertGradleProjectEntity(
        myProject, projectUrl = rootBuildUrl,
        buildId = rootBuildId, path = ":",
        linkedProjectId = "project", identityPath = ":",
      )

      assertGradleBuildEntity(myProject, firstIncludedBuildUrl, externalProjectId, listOf(firstIncludedProjectId))
      assertGradleProjectEntity(
        myProject, projectUrl = firstIncludedBuildUrl,
        buildId = firstIncludedBuildId, path = ":",
        linkedProjectId = ":includedProject1", identityPath = ":includedProject1",
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

    val secondIncludedBuildUrl = rootBuildUrl.parent!!.append("includedProject2")
    val secondIncludedBuildId = GradleBuildEntityId(externalProjectId, secondIncludedBuildUrl)
    val secondIncludedProjectId = GradleProjectEntityId(secondIncludedBuildId, secondIncludedBuildUrl)

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

          assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
          assertEntities<GradleBuildEntity>(myProject, rootBuildId, firstIncludedBuildId, secondIncludedBuildId)
          assertEntities<GradleProjectEntity>(myProject, rootProjectId, firstIncludedProjectId, secondIncludedProjectId)

          assertGradleModuleEntities(
            myProject,
            "project" to rootProjectId,
            "includedProject1" to firstIncludedProjectId,
            "includedProject2" to secondIncludedProjectId
          )

          assertGradleBuildEntity(myProject, rootBuildUrl, externalProjectId, listOf(rootProjectId))
          assertGradleProjectEntity(
            myProject, projectUrl = rootBuildUrl,
            buildId = rootBuildId, path = ":",
            linkedProjectId = "project", identityPath = ":",
          )

          assertGradleBuildEntity(myProject, firstIncludedBuildUrl, externalProjectId, listOf(firstIncludedProjectId))
          assertGradleProjectEntity(
            myProject, projectUrl = firstIncludedBuildUrl,
            buildId = firstIncludedBuildId, path = ":",
            linkedProjectId = ":includedProject1", identityPath = ":includedProject1",
          )

          assertGradleBuildEntity(myProject, secondIncludedBuildUrl, externalProjectId, listOf(secondIncludedProjectId))
          assertGradleProjectEntity(
            myProject, projectUrl = secondIncludedBuildUrl,
            buildId = secondIncludedBuildId, path = ":",
            linkedProjectId = ":includedProject2", identityPath = ":includedProject2",
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

      assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
      assertEntities<GradleBuildEntity>(myProject, rootBuildId, firstIncludedBuildId, secondIncludedBuildId)
      assertEntities<GradleProjectEntity>(myProject, rootProjectId, firstIncludedProjectId, secondIncludedProjectId)
      assertGradleModuleEntities(
        myProject,
        "project" to rootProjectId,
        "includedProject1" to firstIncludedProjectId,
        "includedProject2" to secondIncludedProjectId
      )

      assertGradleBuildEntity(myProject, rootBuildUrl, externalProjectId, listOf(rootProjectId))
      assertGradleProjectEntity(
        myProject, projectUrl = rootBuildUrl,
        buildId = rootBuildId, path = ":",
        linkedProjectId = "project", identityPath = ":",
      )

      assertGradleBuildEntity(myProject, firstIncludedBuildUrl, externalProjectId, listOf(firstIncludedProjectId))
      assertGradleProjectEntity(
        myProject, projectUrl = firstIncludedBuildUrl,
        buildId = firstIncludedBuildId, path = ":",
        linkedProjectId = ":includedProject1", identityPath = ":includedProject1",
      )

      assertGradleBuildEntity(myProject, secondIncludedBuildUrl, externalProjectId, listOf(secondIncludedProjectId))
      assertGradleProjectEntity(
        myProject, projectUrl = secondIncludedBuildUrl,
        buildId = secondIncludedBuildId, path = ":",
        linkedProjectId = ":includedProject2", identityPath = ":includedProject2",
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
  fun `test workspace entities configuration in the Gradle project with buildSrc`() {

    // The buildSrc should be resolved on the second Gradle call for Gradle versions order than 8.0.
    // However, IDEA should keep the old buildSrc modules in the next re-syncs.
    val isBuildSrcResolvedOnSecondCall = isGradleOlderThan("8.0")

    val projectRoot = myProjectRoot.toNioPath()

    val externalProjectId = ExternalProjectEntityId(projectRoot.toCanonicalPath())
    val rootBuildUrl = projectRoot.toVirtualFileUrl(myProject.workspaceModel.getVirtualFileUrlManager())
    val rootBuildId = GradleBuildEntityId(externalProjectId, rootBuildUrl)
    val rootProjectId = GradleProjectEntityId(rootBuildId, rootBuildUrl)

    val buildSrcBuildUrl = rootBuildUrl.append("buildSrc")
    val buildSrcBuildId = GradleBuildEntityId(externalProjectId, buildSrcBuildUrl)
    val buildSrcProjectId = GradleProjectEntityId(buildSrcBuildId, buildSrcBuildUrl)

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      val isBuildSrcShouldBeResolved = AtomicBoolean(!isBuildSrcResolvedOnSecondCall)

      whenSyncPhaseCompleted(GradleSyncPhase.PROJECT_MODEL_PHASE, disposable) {
        contentRootContributorAssertion.trace {
          when {
            !isBuildSrcShouldBeResolved.getAndSet(true) -> {
              assertModules(myProject, "project")

              assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
              assertEntities<GradleBuildEntity>(myProject, rootBuildId)
              assertEntities<GradleProjectEntity>(myProject, rootProjectId)
              assertGradleModuleEntities(myProject, "project" to rootProjectId)

              assertGradleBuildEntity(myProject, rootBuildUrl, externalProjectId, listOf(rootProjectId))
              assertGradleProjectEntity(
                myProject, projectUrl = rootBuildUrl,
                buildId = rootBuildId, path = ":",
                linkedProjectId = "project", identityPath = ":",
              )

              assertContentRoots(myProject, "project", projectRoot)
            }
            isBuildSrcResolvedOnSecondCall -> {
              assertModules(myProject, "project", "project.main", "project.test", "project.buildSrc")

              assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
              assertEntities<GradleBuildEntity>(myProject, rootBuildId, buildSrcBuildId)
              assertEntities<GradleProjectEntity>(myProject, rootProjectId, buildSrcProjectId)
              assertGradleModuleEntities(
                myProject,
                "project" to rootProjectId,
                "project.buildSrc" to buildSrcProjectId
              )

              assertGradleBuildEntity(myProject, rootBuildUrl, externalProjectId, listOf(rootProjectId))
              assertGradleProjectEntity(
                myProject, projectUrl = rootBuildUrl,
                buildId = rootBuildId, path = ":",
                linkedProjectId = "project", identityPath = ":",
              )

              assertGradleBuildEntity(myProject, buildSrcBuildUrl, externalProjectId, listOf(buildSrcProjectId))
              assertGradleProjectEntity(
                myProject,
                projectUrl = buildSrcBuildUrl,
                buildId = buildSrcBuildId, path = ":",
                // for Gradle < 8.0, linkedProjectId and identityPath are calculated incorrectly because buildSrc is synced separately
                linkedProjectId = "project:buildSrc", identityPath = ":",
              )

              assertContentRoots(myProject, "project", projectRoot)
              assertContentRoots(myProject, "project.main", projectRoot.resolve("src/main"))
              assertContentRoots(myProject, "project.test", projectRoot.resolve("src/test"))
              assertContentRoots(myProject, "project.buildSrc", projectRoot.resolve("buildSrc"))
            }
            else -> {
              assertModules(myProject, "project", "project.buildSrc")

              assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
              assertEntities<GradleBuildEntity>(myProject, rootBuildId, buildSrcBuildId)
              assertEntities<GradleProjectEntity>(myProject, rootProjectId, buildSrcProjectId)
              assertGradleModuleEntities(
                myProject,
                "project" to rootProjectId,
                "project.buildSrc" to buildSrcProjectId
              )

              assertGradleBuildEntity(myProject, rootBuildUrl, externalProjectId, listOf(rootProjectId))
              assertGradleProjectEntity(
                myProject, projectUrl = rootBuildUrl,
                buildId = rootBuildId, path = ":",
                linkedProjectId = "project", identityPath = ":"
              )

              assertGradleBuildEntity(myProject, buildSrcBuildUrl, externalProjectId, listOf(buildSrcProjectId))
              assertGradleProjectEntity(
                myProject, projectUrl = buildSrcBuildUrl,
                buildId = buildSrcBuildId, path = ":",
                linkedProjectId = ":buildSrc", identityPath = ":buildSrc"
              )

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

      assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
      assertEntities<GradleBuildEntity>(myProject, rootBuildId, buildSrcBuildId)
      assertEntities<GradleProjectEntity>(myProject, rootProjectId, buildSrcProjectId)
      assertGradleModuleEntities(
        myProject,
        "project" to rootProjectId,
        "project.buildSrc" to buildSrcProjectId
      )

      assertGradleBuildEntity(myProject, rootBuildUrl, externalProjectId, listOf(rootProjectId))
      assertGradleProjectEntity(
        myProject, projectUrl = rootBuildUrl,
        buildId = rootBuildId, path = ":",
        linkedProjectId = "project", identityPath = ":",
      )

      assertGradleBuildEntity(myProject, buildSrcBuildUrl, externalProjectId, listOf(buildSrcProjectId))
      assertGradleProjectEntity(
        myProject, projectUrl = buildSrcBuildUrl,
        buildId = buildSrcBuildId, path = ":",
        // for Gradle < 8.0, linkedProjectId and identityPath are calculated incorrectly because buildSrc is synced separately
        linkedProjectId = when (isBuildSrcResolvedOnSecondCall) {
          true -> "project:buildSrc"
          else -> ":buildSrc" // correct value
        },
        identityPath = when (isBuildSrcResolvedOnSecondCall) {
          true -> ":"
          else -> ":buildSrc" // correct value
        },
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
          assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
          assertEntities<GradleBuildEntity>(myProject, rootBuildId, buildSrcBuildId)
          assertEntities<GradleProjectEntity>(myProject, rootProjectId, buildSrcProjectId)
          assertGradleModuleEntities(
            myProject,
            "project" to rootProjectId,
            "project.buildSrc" to buildSrcProjectId
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
      assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
      assertEntities<GradleBuildEntity>(myProject, rootBuildId, buildSrcBuildId)
      assertEntities<GradleProjectEntity>(myProject, rootProjectId, buildSrcProjectId)
      assertGradleModuleEntities(
        myProject,
        "project" to rootProjectId,
        "project.buildSrc" to buildSrcProjectId
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
  fun `test workspace entities configuration outside project root`() {

    val projectRoot = myProjectRoot.toNioPath()
    val externalProjectRoot = myTestDir.resolve("external/project/root")

    val externalProjectId = ExternalProjectEntityId(projectRoot.toCanonicalPath())
    val rootBuildUrl = projectRoot.toVirtualFileUrl(myProject.workspaceModel.getVirtualFileUrlManager())
    val rootBuildId = GradleBuildEntityId(externalProjectId, rootBuildUrl)
    val rootProjectId = GradleProjectEntityId(rootBuildId, rootBuildUrl)

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

          assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
          assertEntities<GradleBuildEntity>(myProject, rootBuildId)
          assertEntities<GradleProjectEntity>(myProject, rootProjectId)
          assertGradleModuleEntities(myProject, "project" to rootProjectId)

          assertGradleBuildEntity(myProject, rootBuildUrl, externalProjectId, listOf(rootProjectId))
          assertGradleProjectEntity(
            myProject, projectUrl = rootBuildUrl,
            buildId = rootBuildId, path = ":",
            linkedProjectId = "project", identityPath = ":",
          )

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

      assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
      assertEntities<GradleBuildEntity>(myProject, rootBuildId)
      assertEntities<GradleProjectEntity>(myProject, rootProjectId)
      assertGradleModuleEntities(myProject, "project" to rootProjectId)

      assertGradleBuildEntity(myProject, rootBuildUrl, externalProjectId, listOf(rootProjectId))
      assertGradleProjectEntity(
        myProject, projectUrl = rootBuildUrl,
        buildId = rootBuildId, path = ":",
        linkedProjectId = "project", identityPath = ":",
      )

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
  fun `test workspace entities configuration with single source root`() {

    val projectRoot = myProjectRoot.toNioPath()

    val externalProjectId = ExternalProjectEntityId(projectRoot.toCanonicalPath())
    val rootBuildUrl = projectRoot.toVirtualFileUrl(myProject.workspaceModel.getVirtualFileUrlManager())
    val rootBuildId = GradleBuildEntityId(externalProjectId, rootBuildUrl)
    val rootProjectId = GradleProjectEntityId(rootBuildId, rootBuildUrl)

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenSyncPhaseCompleted(GradleSyncPhase.SOURCE_SET_MODEL_PHASE, disposable) {
        contentRootContributorAssertion.trace {
          assertModules(myProject, "project", "project.main", "project.test")

          assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
          assertEntities<GradleBuildEntity>(myProject, rootBuildId)
          assertEntities<GradleProjectEntity>(myProject, rootProjectId)
          assertGradleModuleEntities(myProject, "project" to rootProjectId)

          assertGradleBuildEntity(myProject, rootBuildUrl, externalProjectId, listOf(rootProjectId))
          assertGradleProjectEntity(
            myProject, projectUrl = rootBuildUrl,
            buildId = rootBuildId, path = ":",
            linkedProjectId = "project", identityPath = ":",
          )

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

      assertEntities<ExternalProjectEntity>(myProject, externalProjectId)
      assertEntities<GradleBuildEntity>(myProject, rootBuildId)
      assertEntities<GradleProjectEntity>(myProject, rootProjectId)
      assertGradleModuleEntities(myProject, "project" to rootProjectId)

      assertGradleBuildEntity(myProject, rootBuildUrl, externalProjectId, listOf(rootProjectId))
      assertGradleProjectEntity(
        myProject, projectUrl = rootBuildUrl,
        buildId = rootBuildId, path = ":",
        linkedProjectId = "project", identityPath = ":",
      )

      assertContentRoots(myProject, "project", projectRoot)
      assertContentRoots(myProject, "project.main", projectRoot.resolve("src"))
      assertContentRoots(myProject, "project.test")

      contentRootContributorAssertion.assertListenerFailures()
      contentRootContributorAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once"
      }
    }
  }

  @Test
  fun `test workspace entities creation for a linked project`() {

    val projectRoot = myProjectRoot.toNioPath()
    createSettingsFile {}
    createBuildFile {}

    val rootExternalProjectId = ExternalProjectEntityId(projectRoot.toCanonicalPath())
    val virtualFileManager = myProject.workspaceModel.getVirtualFileUrlManager()
    val rootBuildUrl = projectRoot.toVirtualFileUrl(virtualFileManager)
    val rootBuildId = GradleBuildEntityId(rootExternalProjectId, rootBuildUrl)
    val rootProjectId = GradleProjectEntityId(rootBuildId, rootBuildUrl)

    importProject()

    val linkedProjectRoot = projectRoot.getResolvedPath("../linked-project")
    val linkedExternalProjectId = ExternalProjectEntityId(linkedProjectRoot.toCanonicalPath())
    val linkedBuildUrl = linkedProjectRoot.toVirtualFileUrl(virtualFileManager)
    val linkedBuildId = GradleBuildEntityId(linkedExternalProjectId, linkedBuildUrl)
    val linkedProjectId = GradleProjectEntityId(linkedBuildId, linkedBuildUrl)

    Disposer.newDisposable().use { disposable ->

      val contentRootContributorAssertion = ListenerAssertion()

      whenSyncPhaseCompleted(GradleSyncPhase.PROJECT_MODEL_PHASE, disposable) {
        contentRootContributorAssertion.trace {
          assertModules(myProject, "project", "linked-project")

          assertEntities(myProject, rootExternalProjectId, linkedExternalProjectId)
          assertEntities<GradleBuildEntity>(myProject, rootBuildId, linkedBuildId)
          assertEntities<GradleProjectEntity>(myProject, rootProjectId, linkedProjectId)
          assertGradleModuleEntities(
            myProject,
            "project" to rootProjectId,
            "linked-project" to linkedProjectId
          )

          assertGradleBuildEntity(myProject, rootBuildUrl, rootExternalProjectId, listOf(rootProjectId))
          assertGradleProjectEntity(
            myProject, projectUrl = rootBuildUrl,
            buildId = rootBuildId, path = ":",
            linkedProjectId = "project", identityPath = ":",
          )

          assertGradleBuildEntity(myProject, linkedBuildUrl, linkedExternalProjectId, listOf(linkedProjectId))
          assertGradleProjectEntity(
            myProject, projectUrl = linkedBuildUrl,
            buildId = linkedBuildId, path = ":",
            linkedProjectId = "linked-project", identityPath = ":",
          )

          assertContentRoots(myProject, "project", projectRoot)
          assertContentRoots(myProject, "linked-project", linkedProjectRoot)
        }
      }

      createSettingsFile("../linked-project") {
        setProjectName("linked-project")
      }
      createBuildFile("../linked-project") {
        withJavaPlugin()
      }
      createGradleWrapper("../linked-project")

      val settings = GradleSettings.getInstance(myProject)
      val projectSettings = GradleProjectSettings(linkedProjectRoot.toCanonicalPath())
      settings.linkProject(projectSettings)

      ExternalSystemUtil.refreshProject(linkedProjectRoot.toCanonicalPath(), createImportSpec())

      assertModules(myProject, "project", "linked-project", "linked-project.main", "linked-project.test")

      assertEntities(myProject, rootExternalProjectId, linkedExternalProjectId)
      assertEntities<GradleBuildEntity>(myProject, rootBuildId, linkedBuildId)
      assertEntities<GradleProjectEntity>(myProject, rootProjectId, linkedProjectId)
      assertGradleModuleEntities(
        myProject,
        "project" to rootProjectId,
        "linked-project" to linkedProjectId
      )

      assertGradleBuildEntity(myProject, rootBuildUrl, rootExternalProjectId, listOf(rootProjectId))
      assertGradleProjectEntity(
        myProject, projectUrl = rootBuildUrl,
        buildId = rootBuildId, path = ":",
        linkedProjectId = "project", identityPath = ":",
      )

      assertGradleBuildEntity(myProject, linkedBuildUrl, linkedExternalProjectId, listOf(linkedProjectId))
      assertGradleProjectEntity(
        myProject, projectUrl = linkedBuildUrl,
        buildId = linkedBuildId, path = ":",
        linkedProjectId = "linked-project", identityPath = ":",
      )

      assertContentRoots(myProject, "project", projectRoot)
      assertContentRoots(myProject, "linked-project", linkedProjectRoot)
      assertContentRoots(myProject, "linked-project.main", linkedProjectRoot.resolve("src/main"))
      assertContentRoots(myProject, "linked-project.test", linkedProjectRoot.resolve("src/test"))

      contentRootContributorAssertion.assertListenerFailures()
      contentRootContributorAssertion.assertListenerState(1) {
        "The project info resolution should be started only once."
      }
    }
  }
}