// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.vfs.createDirectory
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixture.GradleExecutionTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixture.GradleExecutionTestFixtureImpl
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleTestFixtureImpl
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo

@TestApplication
abstract class GradleReloadProjectBaseTestCase {

  private lateinit var testInfo: TestInfo

  private var gradleTestFixture: GradleTestFixture? = null
  private var gradleProjectTestFixture: GradleProjectTestFixture? = null
  private var gradleExecutionTestFixture: GradleExecutionTestFixture? = null

  val gradleFixture: GradleTestFixture
    get() = requireNotNull(gradleTestFixture) {
      "Gradle fixture isn't setup. Please use [test] function inside your tests."
    }

  val projectFixture: GradleProjectTestFixture
    get() = requireNotNull(gradleProjectTestFixture) {
      "Gradle fixture isn't setup. Please use [test] function inside your tests."
    }

  val executionFixture: GradleExecutionTestFixture
    get() = requireNotNull(gradleExecutionTestFixture) {
      "Gradle fixture isn't setup. Please use [test] function inside your tests."
    }

  open fun setUp() = Unit

  open fun tearDown() = Unit

  open fun test(gradleVersion: GradleVersion, action: suspend () -> Unit) {
    runAll(
      {
        setUpGradleReloadProjectBaseTestCase(gradleVersion)
        runBlocking {
          action()
        }
      },
      {
        tearDownGradleReloadProjectBaseTestCase()
      }
    )
  }

  @BeforeEach
  fun setUpTestInfo(testInfo: TestInfo) {
    this.testInfo = testInfo
  }

  private fun setUpGradleReloadProjectBaseTestCase(gradleVersion: GradleVersion) {
    gradleTestFixture = GradleTestFixtureImpl(
      className = testInfo.testClass.get().simpleName,
      methodName = testInfo.testMethod.get().name,
      gradleVersion = gradleVersion
    )
    gradleFixture.setUp()

    gradleProjectTestFixture = GradleProjectTestFixtureImpl(gradleFixture)
    projectFixture.setUp()

    gradleExecutionTestFixture = GradleExecutionTestFixtureImpl(
      projectFixture.project,
      projectFixture.projectRoot
    )
    executionFixture.setUp()

    setUp()
  }

  private fun tearDownGradleReloadProjectBaseTestCase() {
    runAll(
      { tearDown() },
      { gradleExecutionTestFixture?.tearDown() },
      { gradleExecutionTestFixture = null },
      { gradleProjectTestFixture?.tearDown() },
      { gradleProjectTestFixture = null },
      { gradleTestFixture?.tearDown() },
      { gradleTestFixture = null }
    )
  }

  interface GradleProjectTestFixture : IdeaTestFixture {

    val project: Project

    val projectName: String

    val projectRoot: VirtualFile
  }

  private class GradleProjectTestFixtureImpl(
    private val testFixture: GradleTestFixture
  ) : GradleProjectTestFixture {

    override lateinit var project: Project
    override lateinit var projectName: String
    override lateinit var projectRoot: VirtualFile

    override fun setUp() {
      runBlocking {
        projectName = "project"
        projectRoot = writeAction {
          testFixture.testRoot.createDirectory(projectName)
        }
        writeAction {
          projectRoot.createSettingsFile {
            setProjectName(projectName)
          }
        }
        project = testFixture.openProject(projectName)
      }
    }

    override fun tearDown() {
      runBlocking {
        project.closeProjectAsync()
      }
    }
  }
}