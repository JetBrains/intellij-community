// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.SdkTestFixture
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.*

internal class GradleTestFixtureFactoryImpl : GradleTestFixtureFactory {

  override fun createGradleJvmTestFixture(
    gradleVersion: GradleVersion
  ): SdkTestFixture {
    return GradleJvmTestFixtureImpl(gradleVersion)
  }

  override fun createFileTestFixture(
    root: VirtualFile,
    configure: FileTestFixture.Builder.() -> Unit
  ): FileTestFixture {
    return FileTestFixtureImpl(root, configure)
  }

  override fun createGradleTestFixture(
    projectName: String,
    gradleVersion: GradleVersion,
    configure: FileTestFixture.Builder.() -> Unit
  ): GradleTestFixture {
    return GradleTestFixtureImpl(projectName, gradleVersion, configure)
  }
}