// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.testFramework.fixtures.SdkTestFixture
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.FileTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleCodeInsightTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleProjectTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixtureFactory

internal class GradleTestFixtureFactoryImpl : GradleTestFixtureFactory {

  override fun createGradleJvmTestFixture(
    gradleVersion: GradleVersion
  ): SdkTestFixture {
    return GradleJvmTestFixtureImpl(gradleVersion)
  }

  override fun createFileTestFixture(
    relativePath: String,
    configure: FileTestFixture.Builder.() -> Unit
  ): FileTestFixture {
    return FileTestFixtureImpl(relativePath, configure)
  }

  override fun createGradleProjectTestFixture(
    projectName: String,
    gradleVersion: GradleVersion,
    configure: FileTestFixture.Builder.() -> Unit
  ): GradleProjectTestFixture {
    return GradleProjectTestFixtureImpl(projectName, gradleVersion, configure)
  }

  override fun createGradleCodeInsightTestFixture(
    projectName: String,
    gradleVersion: GradleVersion,
    configure: FileTestFixture.Builder.() -> Unit
  ): GradleCodeInsightTestFixture {
    return createGradleCodeInsightTestFixture(
      createGradleProjectTestFixture(projectName, gradleVersion, configure)
    )
  }

  override fun createGradleCodeInsightTestFixture(gradleProjectTestFixture: GradleProjectTestFixture): GradleCodeInsightTestFixture {
    return GradleCodeInsightTestFixtureImpl(gradleProjectTestFixture)
  }
}