// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.FileTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleProjectTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory

internal class GradleTestFixtureFactoryImpl : GradleTestFixtureFactory {

  override fun createFileTestFixture(
    relativePath: String,
    configure: FileTestFixture.Builder.() -> Unit
  ): FileTestFixture {
    return FileTestFixtureImpl(relativePath, configure)
  }

  override fun createGradleTestFixture(
    className: String,
    methodName: String,
    gradleVersion: GradleVersion
  ): GradleTestFixture {
    return GradleTestFixtureImpl(className, methodName, gradleVersion)
  }

  override fun createGradleProjectTestFixture(
    projectName: String,
    gradleVersion: GradleVersion,
    configure: FileTestFixture.Builder.() -> Unit
  ): GradleProjectTestFixture {
    return GradleProjectTestFixtureImpl(projectName, gradleVersion, configure)
  }
}