// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures.impl

import com.intellij.testFramework.fixtures.SdkTestFixture
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.*

internal class GradleTestFixtureFactoryImpl : GradleTestFixtureFactory {

  override fun createGradleJvmTestFixture(
    gradleVersion: GradleVersion
  ): SdkTestFixture {
    return GradleJvmTestFixture(gradleVersion)
  }

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