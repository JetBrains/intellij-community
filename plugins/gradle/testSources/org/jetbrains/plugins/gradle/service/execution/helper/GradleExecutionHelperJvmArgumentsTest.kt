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
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.io.path.createDirectories

class GradleExecutionHelperJvmArgumentsTest : GradleExecutionHelperJvmArgumentsTestCase() {

  @Test
  fun `test Gradle JVM options resolution with empty environment`() {
    val workingDirectory = tempDirectory.resolve("project")
      .createDirectories()

    val operation = createOperation()
    val settings = GradleExecutionSettings()
    val buildEnvironment = createBuildEnvironment(workingDirectory)
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    CollectionAssertions.assertEmpty(operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with settings`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    val settings = GradleExecutionSettings().apply {
      withVmOptions(
        "-Dname=value",
        "-Xmx10g"
      )
    }

    val operation = createOperation()
    val buildEnvironment = createBuildEnvironment(projectRoot)
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf(
      "-Dname=value",
      "-Xmx10g"
    ) + IMMUTABLE_JVM_ARGUMENTS
    CollectionAssertions.assertEqualsOrdered(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with BuildEnvironment`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    val buildEnvironment = createBuildEnvironment(projectRoot).apply {
      java.jvmArguments = listOf(
        "-Dname=value",
        "-Xmx10g"
      )
    }

    val operation = createOperation()
    val settings = GradleExecutionSettings()
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    CollectionAssertions.assertEmpty(operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with settings and BuildEnvironment`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    val buildEnvironment = createBuildEnvironment(projectRoot).apply {
      java.jvmArguments = listOf(
        "-Dname=value1",
        "-Dname1=value",
        "-Xmx1g"
      )
    }

    val settings = GradleExecutionSettings().apply {
      withVmOptions(
        "-Dname=value2",
        "-Dname2=value",
        "-Xmx2g"
      )
    }

    val operation = createOperation()
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf(
      "-Dname=value2",
      "-Dname1=value",
      "-Dname2=value",
      "-Xmx2g"
    ) + IMMUTABLE_JVM_ARGUMENTS
    CollectionAssertions.assertEqualsOrdered(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with debug agent in BuildEnvironment`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    val buildEnvironment = createBuildEnvironment(projectRoot).apply {
      java.jvmArguments = listOf(
        "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=*:5005"
      )
    }

    val settings = GradleExecutionSettings().apply {
      withVmOption(
        "-Dname=value"
      )
    }

    val operation = createOperation()
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf(
      "-Dname=value",
      "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=*:5005"
    ) + IMMUTABLE_JVM_ARGUMENTS
    CollectionAssertions.assertEqualsOrdered(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with --add-opens and --add-exports`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    val buildEnvironment = createBuildEnvironment(projectRoot).apply {
      java.jvmArguments = listOf(
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens", "java.prefs/java.util.prefs=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.nio.charset=ALL-UNNAMED",
        "--add-opens", "java.base/java.net=ALL-UNNAMED",
        "--add-opens", "java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "-XX:MaxMetaspaceSize=384m",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-Xms256m",
        "-Xmx512m"
      ) + IMMUTABLE_JVM_ARGUMENTS
    }

    val settings = GradleExecutionSettings().apply {
      withVmOption("-Dname=value")
    }

    val operation = createOperation()
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf(
      "-Dname=value",
      "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
      "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
      "-XX:MaxMetaspaceSize=384m",
      "-XX:+HeapDumpOnOutOfMemoryError",
      "-Xms256m",
      "-Xmx512m"
    ) + IMMUTABLE_JVM_ARGUMENTS
    CollectionAssertions.assertEqualsOrdered(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  @Disabled("Known issues: The JVM options in long option notation cannot be correctly parsed")
  fun `test Gradle JVM options resolution with --add-opens= and --add-exports=`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    val buildEnvironment = createBuildEnvironment(projectRoot).apply {
      java.jvmArguments = listOf(
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        "--add-opens=java.base/java.nio.charset=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "-XX:MaxMetaspaceSize=384m",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-Xms256m",
        "-Xmx512m"
      ) + IMMUTABLE_JVM_ARGUMENTS
    }

    val settings = GradleExecutionSettings().apply {
      withVmOption("-Dname=value")
    }

    val operation = createOperation()
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf(
      "-Dname=value",
      "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
      "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
      "-XX:MaxMetaspaceSize=384m",
      "-XX:+HeapDumpOnOutOfMemoryError",
      "-Xms256m",
      "-Xmx512m"
    ) + IMMUTABLE_JVM_ARGUMENTS
    CollectionAssertions.assertEqualsOrdered(expectedJvmArguments, operation.jvmArguments)
  }
}
