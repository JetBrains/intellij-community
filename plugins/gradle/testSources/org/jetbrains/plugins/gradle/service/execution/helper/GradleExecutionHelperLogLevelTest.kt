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
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.junit.jupiter.api.Test
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

class GradleExecutionHelperLogLevelTest : GradleExecutionHelperLogLeveTestCase() {

  @Test
  fun `test Gradle log level resolution with empty environment`() {
    val workingDirectory = tempDirectory.resolve("project")
      .createDirectories()

    val settings = GradleExecutionSettings()
    val buildEnvironment = createBuildEnvironment(workingDirectory)
    GradleExecutionHelper.setupLogging(settings, buildEnvironment)

    // By default, the "--info" flag is present only in the unit test mode.
    // In production, IDEA doesn't provide any default logging level.
    val expectedArguments = listOf("--info")
    CollectionAssertions.assertEqualsOrdered(expectedArguments, settings.arguments)
  }

  @Test
  fun `test Gradle log level resolution with logging settings in short notation`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    val settings = GradleExecutionSettings().apply {
      withArguments("-d")
    }

    val buildEnvironment = createBuildEnvironment(projectRoot)
    GradleExecutionHelper.setupLogging(settings, buildEnvironment)

    val expectedArguments = listOf("-d")
    CollectionAssertions.assertEqualsOrdered(expectedArguments, settings.arguments)
  }

  @Test
  fun `test Gradle log level resolution with logging settings in long notation`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    val settings = GradleExecutionSettings().apply {
      withArguments("--debug")
    }

    val buildEnvironment = createBuildEnvironment(projectRoot)
    GradleExecutionHelper.setupLogging(settings, buildEnvironment)

    val expectedArguments = listOf("--debug")
    CollectionAssertions.assertEqualsOrdered(expectedArguments, settings.arguments)
  }

  @Test
  fun `test Gradle log level resolution with settings and Gradle properties`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.logging.level=warn")

    val settings = GradleExecutionSettings().apply {
      withArguments("--debug")
    }

    val buildEnvironment = createBuildEnvironment(projectRoot)
    GradleExecutionHelper.setupLogging(settings, buildEnvironment)

    val expectedArguments = listOf("--debug")
    CollectionAssertions.assertEqualsOrdered(expectedArguments, settings.arguments)
  }

  @Test
  fun `test Gradle log level resolution from module directory and with Gradle properties in root directory`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()
    projectRoot.resolve("settings.gradle")
      .createFile()
    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.logging.level=debug")

    val moduleRoot = projectRoot.resolve("module")
      .createDirectories()

    val settings = GradleExecutionSettings()
    val buildEnvironment = createBuildEnvironment(moduleRoot)
    GradleExecutionHelper.setupLogging(settings, buildEnvironment)

    val expectedArguments = listOf("-d")
    CollectionAssertions.assertEqualsOrdered(expectedArguments, settings.arguments)
  }

  @Test
  fun `test Gradle log level resolution from module directory and with Gradle properties in module and root directories`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()
    projectRoot.resolve("settings.gradle")
      .createFile()
    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.logging.level=debug")

    val moduleRoot = projectRoot.resolve("module")
      .createDirectories()
    moduleRoot.resolve("gradle.properties")
      .writeText("org.gradle.logging.level=warn")

    val settings = GradleExecutionSettings()
    val buildEnvironment = createBuildEnvironment(moduleRoot)
    GradleExecutionHelper.setupLogging(settings, buildEnvironment)

    val expectedArguments = listOf("-d")
    CollectionAssertions.assertEqualsOrdered(expectedArguments, settings.arguments)
  }

  @Test
  fun `test Gradle log level resolution from included build directory and with Gradle properties in module and root directories`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()
    projectRoot.resolve("settings.gradle")
      .createFile()
    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.logging.level=warn")

    val moduleRoot = projectRoot.resolve("module")
      .createDirectories()
    moduleRoot.resolve("settings.gradle")
      .createFile()
    moduleRoot.resolve("gradle.properties")
      .writeText("org.gradle.logging.level=debug")

    val settings = GradleExecutionSettings()
    val buildEnvironment = createBuildEnvironment(moduleRoot)
    GradleExecutionHelper.setupLogging(settings, buildEnvironment)

    val expectedArguments = listOf("-d")
    CollectionAssertions.assertEqualsOrdered(expectedArguments, settings.arguments)
  }
}
