// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase.Companion.assertNodeWithDeprecatedGradleWarning
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.buildViewFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createGradleWrapper
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass

@TestApplication
@ParameterizedClass
@AllGradleVersionsSource
class GradleSyncOutputTest(private val gradleVersion: GradleVersion) {

  private val gradleFixture = gradleFixture(gradleVersion)
  private val gradle by gradleFixture

  private val projectRootFixture = tempPathFixture()
  private val projectRoot by projectRootFixture

  private val projectFixture = testFixture {
    // Keep the project fixture dependent on the Gradle fixture, so the project is closed before Gradle listener leak checks run.
    gradleFixture.init()
    val project = projectFixture(projectRootFixture, openAfterCreation = true).init()
    initialized(project) {}
  }
  private val project by projectFixture

  private val buildViewFixture by buildViewFixture(projectFixture)

  @Test
  fun `test sync with lazy task configuration`(): Unit = runBlocking {
    projectRoot.createGradleWrapper(gradleVersion)
    projectRoot.createSettingsFile(gradleVersion) {
      setProjectName(project.name)
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