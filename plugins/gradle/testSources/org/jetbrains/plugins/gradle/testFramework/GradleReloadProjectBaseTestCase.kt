// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.BuildViewTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.utils.vfs.createDirectory
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleTestApplication
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleJvmTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleTestFixtureImpl
import org.jetbrains.plugins.gradle.testFramework.util.ExternalSystemExecutionTracer
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo

@GradleTestApplication
abstract class GradleReloadProjectBaseTestCase {

  private lateinit var testInfo: TestInfo

  private var _gradleJvmFixture: GradleJvmTestFixture? = null
  private val gradleJvmFixture: GradleJvmTestFixture
    get() = requireNotNull(_gradleJvmFixture) {
      "Gradle JVM fixture isn't setup. Please use [test] function inside your tests."
    }

  private var _fileFixture: TempDirTestFixture? = null
  private val fileFixture: TempDirTestFixture
    get() = requireNotNull(_fileFixture) {
      "File fixture isn't setup. Please use [test] function inside your tests."
    }

  private var _gradleFixture: GradleTestFixture? = null
  val gradleFixture: GradleTestFixture
    get() = requireNotNull(_gradleFixture) {
      "Gradle fixture isn't setup. Please use [test] function inside your tests."
    }

  private var _projectFixture: GradleProjectTestFixture? = null
  val projectFixture: GradleProjectTestFixture
    get() = requireNotNull(_projectFixture) {
      "Gradle project fixture isn't setup. Please use [test] function inside your tests."
    }

  private var _buildViewFixture: BuildViewTestFixture? = null
  val buildViewFixture: BuildViewTestFixture
    get() = requireNotNull(_buildViewFixture) {
      "Gradle execution build view fixture wasn't setup. Please use [GradleBaseTestCase.test] function inside your tests."
    }

  open fun setUp() = Unit

  open fun tearDown() = Unit

  open fun test(
    gradleVersion: GradleVersion,
    javaVersionRestriction: JavaVersionRestriction = JavaVersionRestriction.NO,
    action: suspend () -> Unit
  ) {
    runAll(
      {
        setUpGradleReloadProjectBaseTestCase(gradleVersion, javaVersionRestriction)
        runBlocking {
          ExternalSystemExecutionTracer.printExecutionOutputOnException {
            action()
          }
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

  private fun setUpGradleReloadProjectBaseTestCase(gradleVersion: GradleVersion, javaVersionRestriction: JavaVersionRestriction) {
    _gradleJvmFixture = GradleJvmTestFixture(gradleVersion, javaVersionRestriction)
    gradleJvmFixture.setUp()
    gradleJvmFixture.installProjectSettingsConfigurator()

    _fileFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    fileFixture.setUp()
    val testRoot = runBlocking {
      edtWriteAction {
        fileFixture.findOrCreateDir(testInfo.displayName)
          .findOrCreateDirectory(gradleVersion.version)
      }
    }

    _gradleFixture = GradleTestFixtureImpl(testRoot)
    gradleFixture.setUp()

    _projectFixture = GradleProjectTestFixtureImpl(gradleVersion, testRoot, gradleFixture)
    projectFixture.setUp()

    _buildViewFixture = BuildViewTestFixture(projectFixture.project)
    buildViewFixture.setUp()

    setUp()
  }

  private fun tearDownGradleReloadProjectBaseTestCase() {
    runAll(
      { tearDown() },
      { _buildViewFixture?.tearDown() },
      { _buildViewFixture = null },
      { _projectFixture?.tearDown() },
      { _projectFixture = null },
      { _gradleFixture?.tearDown() },
      { _gradleFixture = null },
      { _fileFixture?.tearDown() },
      { _fileFixture = null },
      { _gradleJvmFixture?.tearDown() },
      { _gradleJvmFixture = null }
    )
  }

  interface GradleProjectTestFixture : IdeaTestFixture {

    val project: Project

    val projectName: String

    val projectRoot: VirtualFile
  }

  private class GradleProjectTestFixtureImpl(
    private val gradleVersion: GradleVersion,
    private val testRoot: VirtualFile,
    private val gradleFixture: GradleTestFixture,
  ) : GradleProjectTestFixture {

    override lateinit var project: Project
    override lateinit var projectName: String
    override lateinit var projectRoot: VirtualFile

    override fun setUp() {
      runBlocking {
        projectName = "project"
        projectRoot = edtWriteAction {
          testRoot.createDirectory(projectName)
        }
        edtWriteAction {
          projectRoot.createSettingsFile(gradleVersion) {
            setProjectName(projectName)
          }
        }
        project = gradleFixture.openProject(projectName)
      }
    }

    override fun tearDown() {
      runBlocking {
        project.closeProjectAsync()
      }
    }
  }
}