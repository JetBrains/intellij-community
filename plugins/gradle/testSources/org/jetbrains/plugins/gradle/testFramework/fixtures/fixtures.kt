// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BuildViewTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleJvmTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleTestFixtureImpl
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction

fun gradleJvmFixture(
  gradleVersion: GradleVersion,
  javaVersion: JavaVersionRestriction,
): TestFixture<GradleJvmTestFixture> = testFixture {
  val fixture = GradleJvmTestFixture(gradleVersion, javaVersion)
  fixture.setUp()
  initialized(fixture) {
    fixture.tearDown()
  }
}

@ApiStatus.Experimental
fun gradleJvmFixture(
  gradleVersion: GradleVersion,
  javaVersion: JavaVersionRestriction,
  parentDisposable: Disposable,
): GradleJvmTestFixture {
  val fixture = GradleJvmTestFixture(gradleVersion, javaVersion)
  fixture.setUp()
  parentDisposable.whenDisposed { fixture.tearDown() }
  return fixture
}

fun gradleFixture(): TestFixture<GradleTestFixture> = testFixture {
  val fixture = GradleTestFixtureImpl()
  fixture.setUp()
  initialized(fixture) {
    fixture.tearDown()
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