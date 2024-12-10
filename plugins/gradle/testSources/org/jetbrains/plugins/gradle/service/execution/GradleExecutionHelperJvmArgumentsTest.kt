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
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEmpty
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

@TestApplication
class GradleExecutionHelperJvmArgumentsTest : GradleExecutionHelperJvmArgumentsTestCase() {

  @TempDir
  private lateinit var tempDirectory: Path

  @Test
  fun `test Gradle JVM options resolution with empty environment`() {
    val workingDirectory = tempDirectory.resolve("project")
      .createDirectories()

    val settings = GradleExecutionSettings()

    val operation = createOperation()
    val buildEnvironment = createBuildEnvironment(workingDirectory)
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    assertEmpty(operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with settings`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    val settings = GradleExecutionSettings().apply {
      withVmOptions("-Xmx10g", "-Dname=value")
    }

    val operation = createOperation()
    val buildEnvironment = createBuildEnvironment(projectRoot)
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-Xmx10g", "-Dname=value")
    assertEqualsUnordered(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with gradle properties`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()
    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-Xmx10g -Dname=value")

    val settings = GradleExecutionSettings()

    val operation = createOperation()
    val buildEnvironment = createBuildEnvironment(projectRoot)
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-Xmx10g", "-Dname=value")
    assertEqualsUnordered(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with settings and gradle properties`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()
    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-Xmx10g")

    val settings = GradleExecutionSettings().apply {
      withVmOptions("-Dname=value")
    }

    val operation = createOperation()
    val buildEnvironment = createBuildEnvironment(projectRoot)
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-Xmx10g", "-Dname=value")
    assertEqualsUnordered(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution from module directory and with settings and gradle properties in root directory`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()
    projectRoot.resolve("settings.gradle")
      .createFile()
    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-Xmx10g")

    val moduleRoot = projectRoot.resolve("module")
      .createDirectories()

    val settings = GradleExecutionSettings().apply {
      withVmOptions("-Dname=value")
    }

    val operation = createOperation()
    val buildEnvironment = createBuildEnvironment(moduleRoot)
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-Xmx10g", "-Dname=value")
    assertEqualsUnordered(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution from module directory and with settings and gradle properties in module and root directories`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()
    projectRoot.resolve("settings.gradle")
      .createFile()
    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-Xmx10g")

    val moduleRoot = projectRoot.resolve("module")
      .createDirectories()
    moduleRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-Xmx100000000000g")

    val settings = GradleExecutionSettings().apply {
      withVmOptions("-Dname=value")
    }

    val operation = createOperation()
    val buildEnvironment = createBuildEnvironment(moduleRoot)
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-Xmx10g", "-Dname=value")
    assertEqualsUnordered(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution from included build directory and with settings and gradle properties in module and root directories`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()
    projectRoot.resolve("settings.gradle")
      .createFile()
    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-Xmx100000000000g")

    val moduleRoot = projectRoot.resolve("module")
      .createDirectories()
    moduleRoot.resolve("settings.gradle")
      .createFile()
    moduleRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-Xmx10g")

    val settings = GradleExecutionSettings().apply {
      withVmOptions("-Dname=value")
    }

    val operation = createOperation()
    val buildEnvironment = createBuildEnvironment(moduleRoot)
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-Xmx10g", "-Dname=value")
    assertEqualsUnordered(expectedJvmArguments, operation.jvmArguments)
  }
}
