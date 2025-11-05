// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.codeInsight.fixture

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.io.path.pathString

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

/**
 * Creates a JUnit5 [TestFixture] wrapper for [CodeInsightTestFixture] that allows using it in JUnit5 tests.
 *
 * The fixture will be tied to the [Project] provided by [projectFixture] and for the [Path] provided by [tempDirFixture].
 *
 * Test data is resolved via [TestDataPath] and [TestSubPath] annotations where the first one is used on the class level (and designates
 * the root of the data), while the second one is used on the method level (and designates the test data for the exact test).
 * Please, use `$PROJECT_ROOT` instead of `$CONTENT_ROOT`.
 *
 * If [TestSubPath] is not set, the test name will be used as a subpath similar to the classic IntelliJ tests approach
 * (with `test` prefix removed and the first letter in lowercase).
 */
@TestOnly
fun codeInsightFixture(
  projectFixture: TestFixture<Project>,
  tempDirFixture: TestFixture<Path>,
): TestFixture<CodeInsightTestFixture> = testFixture { context ->
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

    override fun setUp() {}

    override fun tearDown() {}
  }
  val tempDirFixture = object : TempDirTestFixtureImpl() {
    // This method affects the internal temp dir used by the fixture, so we need to override it and not #getTempDir().
    override fun doCreateTempDirectory(): Path = tempDir

    // As the temporary directory is created by the external fixture, we don't need to handle it here.
    override fun deleteOnTearDown(): Boolean = false
  }

  val codeInsightFixture = CodeInsightTestFixtureImpl(projectFixture, tempDirFixture)
  val rootPath = context.findAnnotation(TestDataPath::class.java)?.value?.removePrefix($$"$PROJECT_ROOT/") ?: ""
  val subPath = context.findAnnotation(TestSubPath::class.java)?.value ?: ""
  val homeDir = IdeaTestExecutionPolicy.getHomePathWithPolicy().toNioPathOrNull()
  check(homeDir != null) {
    "Couldn't create nio.Path from ${IdeaTestExecutionPolicy.getHomePathWithPolicy()}"
  }

  codeInsightFixture.testDataPath = homeDir.resolve(rootPath).resolve(subPath).pathString

  codeInsightFixture.setUp()
  initialized(codeInsightFixture) {
    codeInsightFixture.tearDown()
  }
}
