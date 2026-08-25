// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.fixtures

import com.intellij.gradle.testFramework.fixtures.impl.GradleImmutableProjectHolder
import com.intellij.gradle.testFramework.fixtures.impl.GradleImmutableProjectId
import com.intellij.gradle.testFramework.fixtures.impl.GradleJvmTestFixture
import com.intellij.gradle.testFramework.fixtures.impl.GradleTestFixtureImpl
import com.intellij.gradle.testFramework.jvm.JavaVersionRestriction
import com.intellij.gradle.testFramework.projectInfo.GradleProjectInfo
import com.intellij.gradle.testFramework.projectInfo.initProject
import com.intellij.openapi.project.Project
import com.intellij.platform.externalSystem.testFramework.fixtures.workspaceFixture
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.fixtures.BuildViewTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.gradle.util.GradleVersion
import java.nio.file.Path

fun gradleJvmFixture(
  gradleVersion: GradleVersion = GradleVersion.current(),
  javaVersion: JavaVersionRestriction = JavaVersionRestriction.DEFAULT,
): TestFixture<GradleJvmTestFixture> = testFixture {
  val fixture = GradleJvmTestFixture(gradleVersion, javaVersion)
  fixture.setUp()
  initialized(fixture) {
    fixture.tearDown()
  }
}

fun gradleFixture(
  gradleVersion: GradleVersion = GradleVersion.current(),
  javaVersion: JavaVersionRestriction = JavaVersionRestriction.DEFAULT,
): TestFixture<GradleTestFixture> = testFixture {
  val multiProjectFixture = workspaceFixture().init()
  val gradleJvmFixture = gradleJvmFixture(gradleVersion, javaVersion).init()
  val fixture = GradleTestFixtureImpl(multiProjectFixture, gradleJvmFixture, gradleVersion)
  fixture.setUp()
  initialized(fixture) {
    fixture.tearDown()
  }
}

fun gradleProjectRootFixture(
  projectInfo: GradleProjectInfo,
): TestFixture<Path> = testFixture {
  val testRoot = tempPathFixture().init()
  val projectRoot = projectInfo.initProject(testRoot)
  initialized(projectRoot) {}
}

fun TestFixture<GradleTestFixture>.projectFixture(
  projectRootFixture: TestFixture<Path>,
  numProjectSyncs: Int = 1,
): TestFixture<Project> = testFixture {
  val gradle = this@projectFixture.init()
  val projectRoot = projectRootFixture.init()
  val project = gradle.openProject(projectRoot, numProjectSyncs)
  initialized(project) {
    project.closeProjectAsync()
  }
}

/**
 * Provides a Gradle project that is imported once and then reused by every test that asks for the same
 * [projectId] and [gradleVersion], instead of being imported anew for each of them.
 *
 * The project is immutable from a test's point of view: files a test creates, changes, or deletes are reverted
 * once the test finishes, while the project itself is neither closed nor resynced, so indexes and script
 * smartness are preserved. Tests can therefore mutate build scripts and sources with any API and don't have to
 * announce it beforehand.
 *
 * At most one such project is open at a time. Asking for a different [projectId] or Gradle version closes the
 * previous project first, and so does a modification that cannot be reverted automatically.
 *
 * Declare it as an instance field, like every other Gradle fixture, and use
 * [com.intellij.gradle.testFramework.fixtures.application.GradleImmutableProjectTestApplication] instead
 * of [com.intellij.testFramework.junit5.TestApplication], so that the leak trackers tolerate a project that
 * outlives a single test:
 * ```
 * @GradleImmutableProjectTestApplication
 * @ParameterizedClass
 * @AllGradleVersionsSource
 * class MyTest(gradleVersion: GradleVersion) {
 *   private val project by immutableProjectFixture(gradleVersion, "my-project", simpleJavaProjectInfo(gradleVersion))
 * }
 * ```
 * Do not declare [gradleFixture] in the same test class, because it expects a project for each test.
 *
 * @param projectId identifies the project sources, which are cached between runs. Projects with different content
 * must use different ids.
 */
fun immutableProjectFixture(
  gradleVersion: GradleVersion,
  projectId: String,
  projectInfo: GradleProjectInfo,
  javaVersion: JavaVersionRestriction = JavaVersionRestriction.DEFAULT,
): TestFixture<Project> = testFixture("immutableProjectFixture($projectId)") { context ->
  val holder = checkNotNull(GradleImmutableProjectHolder.find(context.extensionContext)) {
    "The immutable Gradle project has no scope. Annotate the test class with @GradleImmutableProjectTestApplication."
  }
  val id = GradleImmutableProjectId(projectId, gradleVersion)
  val immutableProject = holder.acquire(id, projectInfo, javaVersion)
  initialized(immutableProject.project) {
    holder.release()
  }
}

fun buildViewFixture(
  projectFixture: TestFixture<Project> = projectFixture(),
): TestFixture<BuildViewTestFixture> = testFixture {
  val project = projectFixture.init()
  val fixture = BuildViewTestFixture(project)
  fixture.setUp()
  initialized(fixture) {
    fixture.tearDown()
  }
}