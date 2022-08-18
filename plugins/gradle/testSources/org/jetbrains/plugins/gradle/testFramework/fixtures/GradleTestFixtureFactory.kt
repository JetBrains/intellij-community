// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.fixtures

import com.intellij.testFramework.fixtures.SdkTestFixture
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleTestFixtureFactoryImpl

interface GradleTestFixtureFactory {

  fun createGradleJvmTestFixture(
    gradleVersion: GradleVersion
  ): SdkTestFixture

  @ApiStatus.Experimental
  fun createFileTestFixture(
    relativePath: String,
    configure: FileTestFixture.Builder.() -> Unit
  ): FileTestFixture

  fun createGradleTestFixture(
    projectName: String,
    gradleVersion: GradleVersion,
    configure: FileTestFixture.Builder.() -> Unit
  ): GradleTestFixture

  fun createGradleCodeInsightTestFixture(
    projectName: String,
    gradleVersion: GradleVersion,
    configure: FileTestFixture.Builder.() -> Unit
  ): GradleCodeInsightTestFixture

  fun createGradleCodeInsightTestFixture(
    gradleTestFixture: GradleTestFixture
  ): GradleCodeInsightTestFixture

  companion object {
    private val ourInstance = GradleTestFixtureFactoryImpl()

    @JvmStatic
    fun getFixtureFactory(): GradleTestFixtureFactory {
      return ourInstance
    }
  }
}