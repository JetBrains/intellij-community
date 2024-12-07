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

import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.junit.jupiter.api.Test
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

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
      withVmOptions("-Xmx10g", "-Dname=value")
    }

    val operation = createOperation()
    val buildEnvironment = createBuildEnvironment(projectRoot)
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-Xmx10g", "-Dname=value")
    CollectionAssertions.assertEquals(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with Gradle properties`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()
    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-Xmx10g -Dname=value")

    val operation = createOperation()
    val settings = GradleExecutionSettings()
    val buildEnvironment = createBuildEnvironment(projectRoot)
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-Xmx10g", "-Dname=value")
    CollectionAssertions.assertEquals(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with BuildEnvironment`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    val buildEnvironment = createBuildEnvironment(projectRoot).apply {
      java.jvmArguments = listOf("-Xmx10g", "-Dname=value")
    }

    val operation = createOperation()
    val settings = GradleExecutionSettings()
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-Xmx10g", "-Dname=value")
    CollectionAssertions.assertEquals(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with settings and Gradle properties`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-Dname1=value -Dname=value1 -Xmx1g")

    val settings = GradleExecutionSettings().apply {
      withVmOptions("-Dname2=value", "-Dname=value2", "-Xmx2g")
    }

    val operation = createOperation()
    val buildEnvironment = createBuildEnvironment(projectRoot)
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-Dname1=value", "-Dname2=value", "-Dname=value2", "-Xmx2g")
    CollectionAssertions.assertEquals(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with settings and BuildEnvironment`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    val buildEnvironment = createBuildEnvironment(projectRoot).apply {
      java.jvmArguments = listOf("-Dname1=value", "-Dname=value1", "-Xmx1g")
    }

    val settings = GradleExecutionSettings().apply {
      withVmOptions("-Dname2=value", "-Dname=value2", "-Xmx2g")
    }

    val operation = createOperation()
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-Dname1=value", "-Dname2=value", "-Dname=value2", "-Xmx2g")
    CollectionAssertions.assertEquals(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with Gradle properties and BuildEnvironment`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    val buildEnvironment = createBuildEnvironment(projectRoot).apply {
      java.jvmArguments = listOf("-Dname1=value", "-Dname=value1", "-Xmx1g")
    }

    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-Dname2=value -Dname=value2 -Xmx2g")

    val operation = createOperation()
    val settings = GradleExecutionSettings()
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-Dname1=value", "-Dname2=value", "-Dname=value2", "-Xmx2g")
    CollectionAssertions.assertEquals(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with settings, Gradle properties and BuildEnvironment`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    val buildEnvironment = createBuildEnvironment(projectRoot).apply {
      java.jvmArguments = listOf("-Dname1=value", "-Dname=value1", "-Xmx1g")
    }

    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-Dname2=value -Dname=value2 -Xmx2g")

    val settings = GradleExecutionSettings().apply {
      withVmOptions("-Dname3=value", "-Dname=value3", "-Xmx3g")
    }

    val operation = createOperation()
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-Dname1=value", "-Dname2=value", "-Dname3=value", "-Dname=value3", "-Xmx3g")
    CollectionAssertions.assertEquals(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution from module directory and with Gradle properties in root directory`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()
    projectRoot.resolve("settings.gradle")
      .createFile()
    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-Xmx1g -Dname=value")

    val moduleRoot = projectRoot.resolve("module")
      .createDirectories()

    val operation = createOperation()
    val settings = GradleExecutionSettings()
    val buildEnvironment = createBuildEnvironment(moduleRoot)
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-Xmx1g", "-Dname=value")
    CollectionAssertions.assertEquals(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution from module directory and with Gradle properties in module and root directories`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()
    projectRoot.resolve("settings.gradle")
      .createFile()
    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-Xmx1g -Dname=value")

    val moduleRoot = projectRoot.resolve("module")
      .createDirectories()
    moduleRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-Xmx1000g -Dname1000=value")

    val operation = createOperation()
    val settings = GradleExecutionSettings()
    val buildEnvironment = createBuildEnvironment(moduleRoot)
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-Xmx1g", "-Dname=value")
    CollectionAssertions.assertEquals(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution from included build directory and with Gradle properties in module and root directories`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()
    projectRoot.resolve("settings.gradle")
      .createFile()
    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-Xmx1000g -Dname1000=value")

    val moduleRoot = projectRoot.resolve("module")
      .createDirectories()
    moduleRoot.resolve("settings.gradle")
      .createFile()
    moduleRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-Xmx1g -Dname=value")

    val operation = createOperation()
    val settings = GradleExecutionSettings()
    val buildEnvironment = createBuildEnvironment(moduleRoot)
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-Xmx1g", "-Dname=value")
    CollectionAssertions.assertEquals(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with debug agent in BuildEnvironment`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    val buildEnvironment = createBuildEnvironment(projectRoot).apply {
      java.jvmArguments = listOf("-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=*:5005")
    }

    val operation = createOperation()
    val settings = GradleExecutionSettings()
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=*:5005")
    CollectionAssertions.assertEquals(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with same debug agent in BuildEnvironment and Gradle properties`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    val buildEnvironment = createBuildEnvironment(projectRoot).apply {
      java.jvmArguments = listOf("-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=*:5005")
    }

    projectRoot.resolve("gradle.properties")
      .writeText("org.gradle.jvmargs=-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=*:5005")

    val operation = createOperation()
    val settings = GradleExecutionSettings()
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf("-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=*:5005")
    CollectionAssertions.assertEquals(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
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
        "-Xmx512m",
        "-Dfile.encoding=UTF-8",
        "-Duser.country=DE",
        "-Duser.language=en",
        "-Duser.variant"
      )
    }

    val operation = createOperation()
    val settings = GradleExecutionSettings()
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf(
      "-XX:MaxMetaspaceSize=384m",
      "-XX:+HeapDumpOnOutOfMemoryError",
      "-Xms256m",
      "-Xmx512m",
      "-Dfile.encoding=UTF-8",
      "-Duser.country=DE",
      "-Duser.language=en",
      "-Duser.variant"
    )
    CollectionAssertions.assertEquals(expectedJvmArguments, operation.jvmArguments)
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
        "-Xmx512m",
        "-Dfile.encoding=UTF-8",
        "-Duser.country=DE",
        "-Duser.language=en",
        "-Duser.variant"
      )
    }

    val operation = createOperation()
    val settings = GradleExecutionSettings()
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf(
      "-XX:MaxMetaspaceSize=384m",
      "-XX:+HeapDumpOnOutOfMemoryError",
      "-Xms256m",
      "-Xmx512m",
      "-Dfile.encoding=UTF-8",
      "-Duser.country=DE",
      "-Duser.language=en",
      "-Duser.variant"
    )
    CollectionAssertions.assertEquals(expectedJvmArguments, operation.jvmArguments)
  }

  @Test
  fun `test Gradle JVM options resolution with --add-opens and --add-exports in Gradle properties`() {
    val projectRoot = tempDirectory.resolve("project")
      .createDirectories()

    val buildEnvironment = createBuildEnvironment(projectRoot).apply {
      java.jvmArguments = listOf(
        "-XX:MaxMetaspaceSize=512m", "-XX:+HeapDumpOnOutOfMemoryError",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens", "java.prefs/java.util.prefs=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        "--add-opens", "java.base/java.nio.charset=ALL-UNNAMED",
        "--add-opens", "java.base/java.net=ALL-UNNAMED",
        "--add-opens", "java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "-Xmx1g",
        "-Dfile.encoding=UTF-8",
        "-Duser.country=DE",
        "-Duser.language=en",
        "-Duser.variant"
      )
    }

    projectRoot.resolve("gradle.properties")
      .writeText("""
        |org.gradle.jvmargs=-Xmx1g -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError \
        |  --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
        |  --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
        |  --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
        |  --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
        |  --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
      """.trimMargin())

    val operation = createOperation()
    val settings = GradleExecutionSettings()
    GradleExecutionHelper.setupJvmArguments(operation, settings, buildEnvironment)

    val expectedJvmArguments = listOf(
      "-Dfile.encoding=UTF-8",
      "-Duser.country=DE",
      "-Duser.language=en",
      "-Duser.variant",
      "-Xmx1g", "-XX:MaxMetaspaceSize=512m", "-XX:+HeapDumpOnOutOfMemoryError",
      "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
      "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
      "--add-exports", "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
      "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
      "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
    )
    CollectionAssertions.assertEquals(expectedJvmArguments, operation.jvmArguments)
  }
}
