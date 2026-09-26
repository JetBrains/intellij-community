// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.mock.GradleTestBuildEnvironment
import java.nio.file.Path

val GradleTestFixture.buildEnvironment: GradleTestBuildEnvironment
  get() = GradleTestBuildEnvironment.createBuildEnvironment()
    .withGradleVersion(gradleVersion.version)
    .withJavaHome(Path.of(gradleJvmPath))

