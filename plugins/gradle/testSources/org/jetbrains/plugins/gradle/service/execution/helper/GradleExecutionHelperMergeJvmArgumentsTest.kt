/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.execution.helper

import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper
import org.junit.jupiter.api.Test

class GradleExecutionHelperMergeJvmArgumentsTest : GradleExecutionHelperMergeJvmArgumentsTestCase() {

  @Test
  fun `test Gradle JVM arguments merging with empty settings arguments`() {
    val buildEnvironmentJvmArguments = listOf(
      "-X:foo"
    )
    val settingsJvmArguments = emptyList<String>()

    val actualJvmArguments = GradleExecutionHelper.mergeBuildJvmArguments(
      buildEnvironmentJvmArguments,
      settingsJvmArguments
    )

    val expectedJvmArguments = listOf(
      "-X:foo"
    ) + IMMUTABLE_JVM_ARGUMENTS

    CollectionAssertions.assertEqualsOrdered(expectedJvmArguments, actualJvmArguments)
  }

  @Test
  fun `test Gradle JVM arguments merging with empty build environment arguments`() {
    val buildEnvironmentJvmArguments = emptyList<String>()
    val settingsJvmArguments = listOf(
      "-X:foo"
    )

    val actualJvmArguments = GradleExecutionHelper.mergeBuildJvmArguments(
      buildEnvironmentJvmArguments,
      settingsJvmArguments
    )

    val expectedJvmArguments = listOf(
      "-X:foo"
    ) + IMMUTABLE_JVM_ARGUMENTS

    CollectionAssertions.assertEqualsOrdered(expectedJvmArguments, actualJvmArguments)
  }

  @Test
  fun `test Gradle JVM arguments merging with empty build environment and settings arguments`() {
    val buildEnvironmentJvmArguments = emptyList<String>()
    val settingsJvmArguments = emptyList<String>()

    val actualJvmArguments = GradleExecutionHelper.mergeBuildJvmArguments(
      buildEnvironmentJvmArguments,
      settingsJvmArguments
    )

    val expectedJvmArguments = emptyList<String>() + IMMUTABLE_JVM_ARGUMENTS

    CollectionAssertions.assertEqualsOrdered(expectedJvmArguments, actualJvmArguments)
  }

  @Test
  fun `test Gradle JVM arguments merging with system properties`() {
    val buildEnvironmentJvmArguments = listOf(
      "-Dp=val"
    )
    val settingsJvmArguments = listOf(
      "-Dp=newVal"
    )

    val actualJvmArguments = GradleExecutionHelper.mergeBuildJvmArguments(
      buildEnvironmentJvmArguments,
      settingsJvmArguments
    )

    val expectedJvmArguments = listOf(
      "-Dp=newVal"
    ) + IMMUTABLE_JVM_ARGUMENTS

    CollectionAssertions.assertEqualsOrdered(expectedJvmArguments, actualJvmArguments)
  }

  @Test
  fun `test Gradle JVM arguments merging with unique arguments`() {
    val buildEnvironmentJvmArguments = listOf(
      "-Dp=v"
    )
    val settingsJvmArguments = listOf(
      "-X:foo"
    )

    val actualJvmArguments = GradleExecutionHelper.mergeBuildJvmArguments(
      buildEnvironmentJvmArguments,
      settingsJvmArguments
    )

    val expectedJvmArguments = listOf(
      "-Dp=v",
      "-X:foo"
    ) + IMMUTABLE_JVM_ARGUMENTS

    CollectionAssertions.assertEqualsOrdered(expectedJvmArguments, actualJvmArguments)
  }

  @Test
  fun `test Gradle JVM arguments merging with undefined arguments`() {
    val buildEnvironmentJvmArguments = listOf(
      "-Foo", "bar=001",
      "-Foo", "baz=002"
    )
    val settingsJvmArguments = listOf(
      "-Foo", "bar=003",
      "-Foo", "baz=002"
    )

    val actualJvmArguments = GradleExecutionHelper.mergeBuildJvmArguments(
      buildEnvironmentJvmArguments,
      settingsJvmArguments
    )

    val expectedJvmArguments = listOf(
      "-Foo", "bar=003",
      "-Foo", "baz=002"
    ) + IMMUTABLE_JVM_ARGUMENTS

    CollectionAssertions.assertEqualsOrdered(expectedJvmArguments, actualJvmArguments)
  }

  @Test
  fun `test Gradle JVM arguments merging with -Xmx arguments`() {
    val settingsJvmArguments = listOf(
      "-Xmx512",
    )
    val buildEnvironmentJvmArguments = listOf(
      "-Xmx256",
    )

    val actualJvmArguments = GradleExecutionHelper.mergeBuildJvmArguments(
      buildEnvironmentJvmArguments,
      settingsJvmArguments
    )

    val expectedJvmArguments = listOf(
      "-Xmx512",
    ) + IMMUTABLE_JVM_ARGUMENTS

    CollectionAssertions.assertEqualsOrdered(expectedJvmArguments, actualJvmArguments)
  }

  @Test
  fun `test Gradle JVM arguments merging with --add-opens arguments`() {
    val settingsJvmArguments = listOf(
      "--add-opens", "java.base/java.util=ALL-UNNAMED"
    )
    val buildEnvironmentJvmArguments = listOf(
      "--add-opens", "java.base/java.lang=ALL-UNNAMED"
    )

    val actualJvmArguments = GradleExecutionHelper.mergeBuildJvmArguments(
      buildEnvironmentJvmArguments,
      settingsJvmArguments
    )

    val expectedJvmArguments = emptyList<String>() + IMMUTABLE_JVM_ARGUMENTS

    CollectionAssertions.assertEqualsOrdered(expectedJvmArguments, actualJvmArguments)
  }
}
