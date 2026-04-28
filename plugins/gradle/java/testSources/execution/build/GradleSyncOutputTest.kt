// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase.Companion.assertNodeWithDeprecatedGradleWarning
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleTestApplication
import org.jetbrains.plugins.gradle.testFramework.fixtures.buildViewFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleJvmFixture
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createGradleWrapper
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.junit.jupiter.params.ParameterizedTest

@GradleTestApplication
class GradleSyncOutputTest {

  private val projectRootFixture = tempPathFixture()
  private val projectRoot by projectRootFixture

  private val projectFixture = projectFixture(projectRootFixture, openAfterCreation = true)
  private val project by projectFixture

  private val gradleFixture by gradleFixture()

  private val buildViewFixture by buildViewFixture(projectFixture)

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test sync with lazy task configuration`(gradleVersion: GradleVersion): Unit = runBlocking {
    gradleJvmFixture(gradleVersion, JavaVersionRestriction.NO, asDisposable())
      .installProjectSettingsConfigurator(asDisposable())

    projectRoot.createGradleWrapper(gradleVersion)
    projectRoot.createSettingsFile(gradleVersion) {
      setProjectName(project.name)
    }
    gradleFixture.reloadProject(project, projectRoot)
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
    gradleFixture.reloadProject(project, projectRoot)
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
    gradleFixture.reloadProject(project, projectRoot)
    buildViewFixture.assertSyncViewTree {
      assertNode("finished") {
        assertNodeWithDeprecatedGradleWarning(gradleVersion)
      }
    }
  }
}