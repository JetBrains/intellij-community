// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.use
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityId
import com.intellij.platform.testFramework.assertion.WorkspaceAssertions.assertEntities
import com.intellij.platform.testFramework.assertion.listenerAssertion.ListenerAssertion
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.testFramework.assertions.assertVersionCatalogEntities
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class GradleVersionCatalogSyncContributorTest : GradlePhasedSyncTestCase() {

  @Test
  @TargetVersions("7.6+")
  fun `test version catalog entities for a simple project`() {
    val projectRoot = myProjectRoot.toNioPath()

    val externalProjectId = ExternalProjectEntityId(projectRoot.toCanonicalPath())
    val rootBuildUrl = projectRoot.toVirtualFileUrl(myProject.workspaceModel.getVirtualFileUrlManager())
    val rootBuildId = GradleBuildEntityId(externalProjectId, rootBuildUrl)

    createSettingsFile {
      setProjectName("project")
      addCode("""
        dependencyResolutionManagement {
          versionCatalogs {
            customLibs {
              from(files("custom.versions.toml"))
            }
            settingsLibs {
              library("groovy-core", "org.codehaus.groovy", "groovy").version("3.0.5")
            }
          }
        }
      """.trimIndent())
    }
    createBuildFile {}
    createProjectSubFile("gradle/libs.versions.toml", /* language=TOML */ """
      [libraries]
      some_test-library = { module = "org.junit.jupiter:junit-jupiter" }
      """.trimIndent()
    )
    createProjectSubFile("custom.versions.toml", /* language=TOML */ """
      [libraries]
      some_test-library2 = { module = "org.junit.jupiter:junit-jupiter" }
      """.trimIndent()
    )

    Disposer.newDisposable().use { disposable ->
      val syncContributorAssertion = ListenerAssertion()

      whenSyncPhaseCompleted(GradleSyncPhase.ADDITIONAL_MODEL_PHASE, disposable) {
        syncContributorAssertion.trace {
          assertEntities<GradleBuildEntity>(myProject, rootBuildId)
          assertVersionCatalogEntities(
            myProject, rootBuildUrl,
            Pair("libs", projectRoot.resolve("gradle/libs.versions.toml")),
            Pair("customLibs", projectRoot.resolve("custom.versions.toml")),
            // TODO add assertion for settingsLibs, once GradleVersionCatalogEntity creation for catalogs from settings is implemented
            //Pair("settingsLibs", projectRoot.resolve("settings.gradle")),
          )
        }
      }
      importProject()

      assertEntities<GradleBuildEntity>(myProject, rootBuildId)
      assertVersionCatalogEntities(
        myProject, rootBuildUrl,
        Pair("libs", projectRoot.resolve("gradle/libs.versions.toml")),
        Pair("customLibs", projectRoot.resolve("custom.versions.toml")),
        // TODO add assertion for settingsLibs, once GradleVersionCatalogEntity creation for catalogs from settings is implemented
        //Pair("settingsLibs", projectRoot.resolve("settings.gradle")),
      )

      syncContributorAssertion.assertListenerFailures()
      syncContributorAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once"
      }
    }
  }

  @Test
  @TargetVersions("7.6+")
  fun `test version catalog entities for an included build`() {
    val projectRoot = myProjectRoot.toNioPath()

    val externalProjectId = ExternalProjectEntityId(projectRoot.toCanonicalPath())
    val rootBuildUrl = projectRoot.toVirtualFileUrl(myProject.workspaceModel.getVirtualFileUrlManager())
    val rootBuildId = GradleBuildEntityId(externalProjectId, rootBuildUrl)
    createSettingsFile {
      setProjectName("project")
      includeBuild("../includedBuild1")
    }
    createBuildFile {}
    createProjectSubFile("gradle/libs.versions.toml", /* language=TOML */ """
      [libraries]
      some_test-library = { module = "org.junit.jupiter:junit-jupiter" }
      """.trimIndent()
    )

    val includedBuildUrl = rootBuildUrl.parent!!.append("includedBuild1")
    val includedBuildId = GradleBuildEntityId(externalProjectId, includedBuildUrl)
    createSettingsFile("../includedBuild1") {
      addCode("""
        dependencyResolutionManagement {
          versionCatalogs {
            customLibs {
              from(files("custom.versions.toml"))
            }
            settingsLibs {
              library("groovy-core", "org.codehaus.groovy", "groovy").version("3.0.5")
            }
          }
        }
      """.trimIndent())
    }
    createBuildFile("../includedBuild1") {}
    createProjectSubFile("../includedBuild1/gradle/libs.versions.toml", /* language=TOML */ """
      [libraries]
      some_test-library2 = { module = "org.junit.jupiter:junit-jupiter" }
      """.trimIndent()
    )
    createProjectSubFile("../includedBuild1/custom.versions.toml", /* language=TOML */ """
      [libraries]
      some_test-library3 = { module = "org.junit.jupiter:junit-jupiter" }
      """.trimIndent()
    )

    Disposer.newDisposable().use { disposable ->
      val syncContributorAssertion = ListenerAssertion()

      whenSyncPhaseCompleted(GradleSyncPhase.ADDITIONAL_MODEL_PHASE, disposable) {
        syncContributorAssertion.trace {
          assertEntities<GradleBuildEntity>(myProject, rootBuildId, includedBuildId)
          assertVersionCatalogEntities(
            myProject, rootBuildUrl,
            Pair("libs", projectRoot.resolve("gradle/libs.versions.toml")),
          )
          assertVersionCatalogEntities(
            myProject, includedBuildUrl,
            Pair("libs", projectRoot.resolve("../includedBuild1/gradle/libs.versions.toml")),
            Pair("customLibs", projectRoot.resolve("../includedBuild1/custom.versions.toml")),
            // TODO add assertion for settingsLibs, once GradleVersionCatalogEntity creation for catalogs from settings is implemented
            //Pair("settingsLibs", projectRoot.resolve("../includedBuild1/settings.gradle")),
          )
        }
      }
      importProject()

      assertEntities<GradleBuildEntity>(myProject, rootBuildId, includedBuildId)
      assertVersionCatalogEntities(
        myProject, rootBuildUrl,
        Pair("libs", projectRoot.resolve("gradle/libs.versions.toml")),
      )
      assertVersionCatalogEntities(
        myProject, includedBuildUrl,
        Pair("libs", projectRoot.resolve("../includedBuild1/gradle/libs.versions.toml")),
        Pair("customLibs", projectRoot.resolve("../includedBuild1/custom.versions.toml")),
        // TODO add assertion for settingsLibs, once GradleVersionCatalogEntity creation for catalogs from settings is implemented
        //Pair("settingsLibs", projectRoot.resolve("../includedBuild1/settings.gradle")),
      )

      syncContributorAssertion.assertListenerFailures()
      syncContributorAssertion.assertListenerState(1) {
        "The project loaded phase should be finished only once"
      }
    }
  }

  //https://docs.gradle.org/current/userguide/version_catalogs.html#sec:buildsrc-version-catalog
  @Test
  @TargetVersions("7.6+")
  fun `test version catalog entities for buildSrc`() {
    // The buildSrc should be resolved on the second Gradle call for Gradle versions order than 8.0.
    // However, IDEA should keep the old buildSrc modules in the next re-syncs.
    val isBuildSrcResolvedOnSecondCall = isGradleOlderThan("8.0")

    val projectRoot = myProjectRoot.toNioPath()

    val externalProjectId = ExternalProjectEntityId(projectRoot.toCanonicalPath())
    val rootBuildUrl = projectRoot.toVirtualFileUrl(myProject.workspaceModel.getVirtualFileUrlManager())
    val rootBuildId = GradleBuildEntityId(externalProjectId, rootBuildUrl)
    createSettingsFile {
      setProjectName("project")
    }
    createBuildFile {}

    val buildSrcBuildUrl = rootBuildUrl.append("buildSrc")
    val buildSrcBuildId = GradleBuildEntityId(externalProjectId, buildSrcBuildUrl)
    createSettingsFile("buildSrc") {
      addCode("""
        dependencyResolutionManagement {
          versionCatalogs {
            customLibs {
              from(files("custom.versions.toml"))
            }
            settingsLibs {
              library("groovy-core", "org.codehaus.groovy", "groovy").version("3.0.5")
            }
          }
        }
      """.trimIndent())
    }
    createBuildFile("buildSrc") {}
    createProjectSubFile("buildSrc/custom.versions.toml", /* language=TOML */ """
      [libraries]
      some_test-library = { module = "org.junit.jupiter:junit-jupiter" }
      """.trimIndent()
    )

    val isBuildSrcShouldBeResolved = AtomicBoolean(!isBuildSrcResolvedOnSecondCall)
    Disposer.newDisposable().use { disposable ->
      val syncContributorAssertion = ListenerAssertion()

      whenSyncPhaseCompleted(GradleSyncPhase.ADDITIONAL_MODEL_PHASE, disposable) {
        syncContributorAssertion.trace {
          when {
            !isBuildSrcShouldBeResolved.getAndSet(true) -> {
              assertEntities<GradleBuildEntity>(myProject, rootBuildId)
              assertVersionCatalogEntities(myProject, buildSrcBuildUrl) {
                "No GradleVersionCatalogEntity expected."
              }
            }
            isBuildSrcResolvedOnSecondCall -> {
              assertEntities<GradleBuildEntity>(myProject, rootBuildId, buildSrcBuildId)
              assertVersionCatalogEntities(
                myProject, buildSrcBuildUrl,
                Pair("customLibs", projectRoot.resolve("buildSrc/custom.versions.toml")),
                // TODO add assertion for settingsLibs, once GradleVersionCatalogEntity creation for catalogs from settings is implemented
                //Pair("settingsLibs", projectRoot.resolve("buildSrc/settings.gradle")),
              )
            }
            else -> {
              assertEntities<GradleBuildEntity>(myProject, rootBuildId, buildSrcBuildId)
              assertVersionCatalogEntities(
                myProject, buildSrcBuildUrl,
                Pair("customLibs", projectRoot.resolve("buildSrc/custom.versions.toml")),
                // TODO add assertion for settingsLibs, once GradleVersionCatalogEntity creation for catalogs from settings is implemented
                //Pair("settingsLibs", projectRoot.resolve("buildSrc/settings.gradle")),
              )
            }
          }
        }
      }
      importProject()

      assertEntities<GradleBuildEntity>(myProject, rootBuildId, buildSrcBuildId)
      assertVersionCatalogEntities(
        myProject, buildSrcBuildUrl,
        Pair("customLibs", projectRoot.resolve("buildSrc/custom.versions.toml")),
      )

      syncContributorAssertion.assertListenerFailures()
      if (isBuildSrcResolvedOnSecondCall) {
        syncContributorAssertion.assertListenerState(2) {
          "The project loaded phase should be finished twice. " +
          "The Gradle older than 8.0 cannot resolve buildSrc by the one Gradle call."
        }
      }
      else {
        syncContributorAssertion.assertListenerState(1) {
          "The project loaded phase should be finished only once"
        }
      }
    }
  }

  // TODO implement
  // https://docs.gradle.org/current/userguide/version_catalogs.html#sec:importing-published-catalog
  @Ignore("Not yet implemented")
  fun `test version catalog entities for published catalog`() {}
}