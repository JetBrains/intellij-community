// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.fixture

import com.intellij.idea.AppMode
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestContext
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.pathInProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.junit5.fixture.vfsDeletedTempPathFixture
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

private const val COMMUNITY_PATH_PREFIX = "community"

/**
 * Use for JUnit5 tests to set the path to the test data on the method level.
 *
 * This allows having test method names in a more readable format (via backticks) while keeping the test data folder structure concise.
 *
 * @see codeInsightFixture
 * @see TestDataPath
 */
@Target(AnnotationTarget.FUNCTION)
annotation class TestSubPath(val value: String)

// In JUnit5 the IdeaProjectTestFixture passed to CodeInsightTestFixtureImpl does not close the project on tearDown, which means
// the pointers are not guaranteed to be disposed when the codeInsightFixture is torn down and attempts to check.
// JUnit5 does its leak-checking through TestApplicationLeakTrackerExtension, so we do not miss
// any leaks by disabling tracking here.
@TestOnly
private class NonTrackingCodeInsightTestFixture(
  project: IdeaProjectTestFixture,
  tempDir: TempDirTestFixture,
) : CodeInsightTestFixtureImpl(project, tempDir) {
  override fun shouldTrackVirtualFilePointers(): Boolean = false
}

/**
 * Creates a JUnit5 [TestFixture] wrapper for [CodeInsightTestFixture] that allows using it in JUnit5 tests.
 *
 * The fixture will be tied to the [Project] provided by [projectFixture] and for the [Path] provided by [tempDirFixture].
 *
 * Test data is resolved via [TestDataPath] and [TestSubPath] annotations.
 * [TestDataPath] is used on the class level and designates the root of the data.
 * It can use the `$PROJECT_ROOT` variable (note that `$CONTENT_ROOT` isn't supported).
 * [TestSubPath] is used on the method level and designates the test data for the exact test.
 *
 * If [TestSubPath] is not set, the subpath will be empty, but you can then construct it manually, e.g., from the test method name,
 * using [com.intellij.testFramework.junit5.fixture.testNameFixture].
 */
@TestOnly
fun codeInsightFixture(
  projectFixture: TestFixture<Project>,
  tempDirFixture: TestFixture<Path>,
): TestFixture<CodeInsightTestFixture> = codeInsightFixture(projectFixture, tempDirFixture) { project, tempDir ->
  NonTrackingCodeInsightTestFixture(project, tempDir)
}

@TestOnly
fun <T: CodeInsightTestFixture> codeInsightFixture(
  projectFixture: TestFixture<Project>,
  tempDirFixture: TestFixture<Path>,
  createFixture: (IdeaProjectTestFixture, TempDirTestFixture) -> T,
): TestFixture<T> = testFixture { context ->
  val project = projectFixture.init()
  val tempDir = tempDirFixture.init()

  val projectFixture = object : IdeaProjectTestFixture {
    override fun getProject(): Project = project

    override fun getModule(): Module {
      check(project.modules.isNotEmpty()) {
        "At least one module is required for the project. Use TestFixture<Project>.moduleFixture() to register one in your test class."
      }
      return project.modules[0]
    }

    override fun setUp() {
      TestApplicationManager.getInstance().setDataProvider(TestDataProvider(project))
    }

    override fun tearDown() {
      TestApplicationManager.getInstance().setDataProvider(null)
    }
  }
  val tempDirFixture = object : TempDirTestFixtureImpl() {
    // This method affects the internal temp dir used by the fixture, so we need to override it and not #getTempDir().
    override fun doCreateTempDirectory(): Path = tempDir

    // As the temporary directory is created by the external fixture, we don't need to handle it here.
    override fun deleteOnTearDown(): Boolean = false
  }

  val codeInsightFixture = createFixture(projectFixture, tempDirFixture)

  codeInsightFixture.testDataPath = getTestDataPathString(context)

  codeInsightFixture.setUp()
  initialized(codeInsightFixture) {
    codeInsightFixture.tearDown()
  }
}

/**
 * Creates a [Project] fixture that is ready for [codeInsightFixture].
 *
 * The project is opened in a temporary directory.
 * It has one module, and the module uses the project directory as its content and source root.
 * Use it with the [codeInsightFixture] overload that takes only the project fixture:
 *
 * ```kotlin
 * private val projectFixture = codeInsightProjectFixture()
 * private val myFixture by codeInsightFixture(projectFixture)
 * ```
 *
 * Declare it as an instance property, so that each test gets a new project.
 * Do not declare it in a companion object.
 * The [codeInsightFixture] overload does not delete the files that a test adds.
 * In a shared project, the next test sees the files of an earlier test.
 * To share one project between the tests of a class, use [codeInsightModuleFixture].
 */
@TestOnly
fun codeInsightProjectFixture(): TestFixture<Project> = testFixture("codeInsightProjectFixture") {
  val pathFixture = tempPathFixture()
  val projectFixture = projectFixture(pathFixture, openAfterCreation = true)
  projectFixture.moduleFixture(pathFixture, addPathToSourceRoot = true).init()
  initialized(projectFixture.init()) {}
}

/**
 * Creates a [CodeInsightTestFixture] that uses the directory of the [projectFixture] project as its temporary directory.
 *
 * Use it with [codeInsightProjectFixture]. The project must contain at least one module.
 * Declare the result as an instance property, so that each test gets its own [CodeInsightTestFixture].
 */
@TestOnly
fun codeInsightFixture(projectFixture: TestFixture<Project>): TestFixture<CodeInsightTestFixture> =
  codeInsightFixture(projectFixture, projectFixture.pathInProjectFixture(Path("")))

/**
 * Creates a [CodeInsightTestFixture] with its own temporary directory and module in the [projectFixture] project.
 *
 * Each test gets a new temporary directory and a new module of the [moduleTypeId] type.
 * The module uses the directory as its content and source root.
 * So a test does not see the files of an earlier test.
 * The project directory is not the module directory.
 * When the code under test needs the project directory, use [codeInsightProjectFixture].
 *
 * Declare [projectFixture] in a companion object to share one opened project between the tests of a class:
 *
 * ```kotlin
 * companion object {
 *   private val projectFixture = projectFixture(openAfterCreation = true)
 * }
 *
 * private val myFixture by codeInsightModuleFixture(projectFixture)
 * ```
 */
@JvmOverloads
@TestOnly
fun codeInsightModuleFixture(
  projectFixture: TestFixture<Project>,
  moduleTypeId: String = "",
): TestFixture<CodeInsightTestFixture> =
  testFixture("codeInsightModuleFixture") {
    val pathFixture = vfsDeletedTempPathFixture()
    val moduleFixture = projectFixture.moduleFixture(pathFixture, addPathToSourceRoot = true, moduleTypeId = moduleTypeId)
    // The editors of the test must close before the module is disposed.
    // Otherwise, the roots change of the module starts editor jobs that run in the next test.
    val codeInsightFixture = codeInsightFixture(projectFixture, pathFixture).dependsOn(moduleFixture)
    initialized(codeInsightFixture.init()) {}
  }

private fun getTestDataPathString(context: TestContext): String {
  val rootPath = context.findAnnotation(TestDataPath::class.java)?.value?.removePrefix($$"$PROJECT_ROOT/") ?: ""
  val subPath = context.findAnnotation(TestSubPath::class.java)?.value ?: ""
  val homeDir = getHomePath()
  val resolvedPath = homeDir.resolve(rootPath).resolve(subPath)
  if (resolvedPath.exists()) {
   return resolvedPath.pathString
  }
  // If the project opened as IJ community, then the test path for community will be duplicated, e.g. it is $HOME/community/community/...
  // To handle this scenario, we are trying to resolve the path $HOME/community/../community/...
  check(rootPath.startsWith(COMMUNITY_PATH_PREFIX)) {
    "The test data path is not located in community folder, but it doesn't exist in the ultimate."
  }
  return homeDir.resolve("../").resolve(rootPath).resolve(subPath).pathString
}

private fun getHomePath(): Path {
  val homeDir = IdeaTestExecutionPolicy.getHomePathWithPolicy().toNioPathOrNull()
  check(homeDir != null) {
    "Couldn't create nio.Path from ${IdeaTestExecutionPolicy.getHomePathWithPolicy()}"
  }

  return if (AppMode.isRunningFromDevBuild()) {
    val resolvedHomeDir = AppMode.getDevIdeaProjectDir()
    check(resolvedHomeDir != null) {
      "Couldn't find dev-idea project directory"
    }

    Path.of(resolvedHomeDir)
  }
  else {
    homeDir
  }
}
