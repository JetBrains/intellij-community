// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase.Companion.assertNodeWithDeprecatedGradleWarning
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.buildViewFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleProjectRootFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.projectFixture
import org.jetbrains.plugins.gradle.testFramework.projectInfo.simpleJavaProjectInfo
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass

@TestApplication
@ParameterizedClass
@AllGradleVersionsSource
class GradleSyncOutputTest(private val gradleVersion: GradleVersion) {

  private val gradleFixture = gradleFixture(gradleVersion)
  private val gradle by gradleFixture

  private val projectInfo = simpleJavaProjectInfo(gradleVersion)
  private val projectRootFixture = gradleProjectRootFixture(projectInfo)
  private val projectRoot by projectRootFixture

  private val projectFixture = gradleFixture.projectFixture(projectRootFixture)
  private val project by projectFixture

  private val buildViewFixture by buildViewFixture(projectFixture)

  @Test
  fun `test sync with lazy task configuration`(): Unit = runBlocking {
    projectRoot.createBuildFile(gradleVersion) {
      withJavaPlugin()
    }
    gradle.syncProject(project, projectRoot)
    buildViewFixture.assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning(gradleVersion)
      }
    }

    projectRoot.createBuildFile(gradleVersion) {
      withJavaPlugin()
      withPostfix {
        registerTask("my-jar-task", "Jar") {
          call("project.configurations.create", "my-jar-configuration")
        }
      }
    }
    gradle.syncProject(project, projectRoot)
    buildViewFixture.assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning(gradleVersion)
      }
    }

    projectRoot.createBuildFile(gradleVersion) {
      withJavaPlugin()
      withPostfix {
        registerTask("my-task") {
          call("project.configurations.create", "my-configuration")
        }
        registerTask("my-jar-task", "Jar") {
          call("project.configurations.create", "my-jar-configuration")
        }
      }
    }
    gradle.syncProject(project, projectRoot)
    buildViewFixture.assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning(gradleVersion)
      }
    }
  }
}