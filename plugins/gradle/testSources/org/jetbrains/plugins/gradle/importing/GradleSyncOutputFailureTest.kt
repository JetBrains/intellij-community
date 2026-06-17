// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.platform.testFramework.assertion.treeAssertion.SimpleTreeAssertion.NodeMatcher.Companion.or
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase.Companion.assertNodeWithDeprecatedGradleWarning
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.buildViewFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.projectFixture
import org.jetbrains.plugins.gradle.testFramework.projectInfo.buildFile
import org.jetbrains.plugins.gradle.testFramework.projectInfo.gradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.initProject
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass

@TestApplication
@ParameterizedClass
@AllGradleVersionsSource
@TargetVersions("9.3+")
@RegistryKey("gradle.use.resilient.model.fetch.unstable", true.toString())
class GradleSyncOutputFailureTest(private val gradleVersion: GradleVersion) {

  private val testRootFixture = tempPathFixture()
  private val testRoot by testRootFixture

  private val gradleFixture = gradleFixture(gradleVersion)
  private val gradle by gradleFixture

  private val projectFixture = gradleFixture.projectFixture(testRootFixture, numProjectSyncs = 0)
  private val project by projectFixture

  private val buildView by buildViewFixture(projectFixture)

  @Test
  fun `test sync reports task initialization failure`(): Unit = runBlocking {
    val projectInfo = gradleProjectInfo(gradleVersion) {
      buildFile {
        addPrefix("""
          |open class BrokenIdeaProjectTask : DefaultTask() {
          |  init {
          |    throw RuntimeException("Task initialization failure")
          |  }
          |}
        """.trimMargin())
        registerTask("brokenIdeaProjectTask", "BrokenIdeaProjectTask")
      }
    }

    val projectRoot = projectInfo.initProject(testRoot)

    gradle.linkProject(project, projectRoot)

    buildView.assertSyncViewTree {
      assertNode("failed" or "finished") {
        assertNodeWithDeprecatedGradleWarning(gradleVersion)
        assertNode("build.gradle.kts") {
          assertNode("Could not create task ':brokenIdeaProjectTask'.")
        }
      }
    }
  }
}
