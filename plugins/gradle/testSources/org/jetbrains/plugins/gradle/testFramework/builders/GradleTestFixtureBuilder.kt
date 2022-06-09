// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.builders

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture

interface GradleTestFixtureBuilder {

  val projectName: String

  fun createFixture(gradleVersion: GradleVersion): GradleTestFixture
}