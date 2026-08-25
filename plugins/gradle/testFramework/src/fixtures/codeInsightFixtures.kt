// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.fixtures

import com.intellij.gradle.testFramework.projectInfo.GradleProjectInfoBuilder
import com.intellij.gradle.testFramework.projectInfo.gradleProjectInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.JavaCodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager

/**
 * Provides a [JavaCodeInsightTestFixture] on top of an already opened Gradle project, which is what makes
 * code-insight testing possible against a project produced by a real Gradle import rather than a light one.
 *
 * The code-insight fixture is created and torn down for every test, while the project behind it is not. Combined
 * with [immutableProjectFixture], this is the fixture-based replacement for
 * [com.intellij.gradle.testFramework.GradleCodeInsightBaseTestCase].
 */
fun gradleCodeInsightFixture(
  projectFixture: TestFixture<Project>,
): TestFixture<JavaCodeInsightTestFixture> = testFixture("gradleCodeInsightFixture") {
  val project = projectFixture.init()
  val fixture = GradleCodeInsightTestFixture(project)
  fixture.setUp()
  initialized(fixture) {
    fixture.tearDown()
  }
}

/**
 * Provides a code-insight fixture on an immutable Gradle project, which [configure] declares.
 * The project name, the project directory and the `projectId` of [immutableProjectFixture] are all equal to [projectName].
 *
 * @param projectName identifies the project sources, which are cached between runs. Projects with different content
 * ([gradleDsl] + [configure]) must use different names.
 */
fun gradleCodeInsightFixture(
  gradleVersion: GradleVersion,
  projectName: String,
  gradleDsl: GradleDsl = GradleDsl.KOTLIN,
  configure: GradleProjectInfoBuilder.() -> Unit,
): TestFixture<JavaCodeInsightTestFixture> {
  val projectInfo = gradleProjectInfo(gradleVersion, projectName, gradleDsl, configure)
  val projectFixture = immutableProjectFixture(gradleVersion, projectName, projectInfo)
  return gradleCodeInsightFixture(projectFixture)
}

private class GradleDataProvider(private val project: Project) : DataProvider {

  override fun getData(dataId: String): Any? =
    when {
      CommonDataKeys.PROJECT.`is`(dataId) -> project
      CommonDataKeys.EDITOR.`is`(dataId) -> FileEditorManager.getInstance(project).selectedTextEditor
      else -> null
    }
}

private class GradleIdeaProjectTestFixture(private val project: Project) : IdeaProjectTestFixture {

  private lateinit var testDisposable: Disposable

  override fun getProject(): Project = project

  /**
   * The root module of the Gradle project, named after the project itself.
   */
  override fun getModule(): Module = project.modules.single { it.name == project.name }

  override fun getTestRootDisposable(): Disposable = testDisposable

  override fun setUp() {
    testDisposable = Disposer.newDisposable()
    TestApplicationManager.getInstance().setDataProvider(GradleDataProvider(project), testDisposable)
  }

  override fun tearDown() {
    Disposer.dispose(testDisposable)
  }
}

private class GradleCodeInsightTestFixture(project: Project) : JavaCodeInsightTestFixtureImpl(
  GradleIdeaProjectTestFixture(project),
  TempDirTestFixtureImpl()
) {

  // The project outlives this fixture, so its pointers aren't disposed of when the fixture tears down.
  // They are checked at the @AfterAll bound instead, see GradleImmutableProjectTestApplication.
  override fun shouldTrackVirtualFilePointers(): Boolean = false

  init {
    allowTreeAccessForAllFiles()
  }

  override fun setUp() {
    super.setUp()
    GradleBuildClasspathManager.getInstance(project).reload()
  }
}
