// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework

import com.intellij.gradle.testFramework.fixtures.FileTestFixture
import com.intellij.gradle.testFramework.fixtures.GradleProjectTestFixture
import com.intellij.gradle.testFramework.fixtures.impl.GradleProjectTestFixtureImpl
import com.intellij.gradle.testFramework.jvm.JavaVersionRestriction
import org.gradle.util.GradleVersion

interface GradleTestFixtureBuilder {

  val projectName: String

  fun createFixture(gradleVersion: GradleVersion): GradleProjectTestFixture

  companion object {

    fun create(
      projectName: String,
      javaVersionRestriction: JavaVersionRestriction = JavaVersionRestriction.DEFAULT,
      configure: FileTestFixture.Builder.(GradleVersion) -> Unit
    ): GradleTestFixtureBuilder {
      return object : GradleTestFixtureBuilder {
        override val projectName: String = projectName
        override fun createFixture(gradleVersion: GradleVersion): GradleProjectTestFixture {
          return GradleProjectTestFixtureImpl(projectName, projectName, gradleVersion, javaVersionRestriction) {
              configure(gradleVersion)
          }
        }
      }
    }
  }
}