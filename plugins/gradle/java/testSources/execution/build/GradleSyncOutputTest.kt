// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build

import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleTestApplication
import org.jetbrains.plugins.gradle.testFramework.fixtures.buildViewFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleJvmFixture
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest

@GradleTestApplication
class GradleSyncOutputTest {

  private val testDisposable by disposableFixture()

  private val projectRootFixture = tempPathFixture()
  private val projectRoot by projectRootFixture

  private val projectFixture = projectFixture(projectRootFixture, openAfterCreation = true)
  private val project by projectFixture

  private val gradleFixture by gradleFixture()

  private val buildViewFixture by buildViewFixture(projectFixture)

  @BeforeEach
  fun setUpGradleReloadProjectBaseTestCase() {
    AutoImportProjectTracker.enableAutoReloadInTests(testDisposable)
    AutoImportProjectTracker.enableAsyncAutoReloadInTests(testDisposable)
    ExternalSystemImportingTestCase.installExecutionOutputPrinter(testDisposable)
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test sync with lazy task configuration`(gradleVersion: GradleVersion): Unit = runBlocking {
    gradleJvmFixture(gradleVersion, JavaVersionRestriction.NO, asDisposable())
      .installProjectSettingsConfigurator(asDisposable())

    projectRoot.createSettingsFile(gradleVersion) {
      setProjectName(project.name)
    }
    gradleFixture.reloadProject(project, projectRoot)
    buildViewFixture.assertSyncViewTree {
      assertNode("finished")
    }

    projectRoot.createBuildFile(gradleVersion) {
      withJavaPlugin()
      withPostfix {
        call("tasks.register<Jar>", string("my-jar-task")) {
          call("project.configurations.create", "my-jar-configuration")
        }
      }
    }
    gradleFixture.reloadProject(project, projectRoot)
    buildViewFixture.assertSyncViewTree {
      assertNode("finished")
    }

    projectRoot.createBuildFile(gradleVersion) {
      withJavaPlugin()
      withPostfix {
        call("tasks.register", string("my-task")) {
          call("project.configurations.create", "my-configuration")
        }
        call("tasks.register<Jar>", string("my-jar-task")) {
          call("project.configurations.create", "my-jar-configuration")
        }
      }
    }
    gradleFixture.reloadProject(project, projectRoot)
    buildViewFixture.assertSyncViewTree {
      assertNode("finished")
    }
  }
}