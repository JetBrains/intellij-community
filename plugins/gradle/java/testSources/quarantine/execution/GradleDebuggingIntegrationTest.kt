// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.quarantine.execution

import com.intellij.openapi.util.io.systemIndependentPath
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.execution.GradleDebuggingIntegrationTestCase
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isJunit5Supported
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createGradleWrapper
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.io.File
import java.util.*

class GradleDebuggingIntegrationTest : GradleDebuggingIntegrationTestCase() {

  @Test
  //The general availability of the Gradle Configuration Cache was announced only since Gradle 8.1
  @TargetVersions("8.1+")
  fun `gradle configuration cache is not corrupted between run and debug`() {
    createGradlePropertiesFile(content = "org.gradle.configuration-cache=true".trimIndent())

    val message = UUID.randomUUID().toString()

    importProject {
      withMavenCentral()
      applyPlugin("java")

      addPostfix("""
        task myTask {
          dependsOn 'clean'
          dependsOn 'build'
          doFirst {
            System.out.println("$message")
          }
          outputs.cacheIf { true }
        }
      """.trimIndent())
    }

    val runOutput = executeRunConfiguration("myTask", isDebugServerProcess = false)
    assertThat(runOutput)
      .contains("Configuration cache entry stored.")
      .contains("BUILD SUCCESSFUL")
      .contains(message)
    if (isGradleOlderThan("9.0")) {
      assertThat(runOutput).contains("0 problems were found storing the configuration cache.")
    }

    val debugOutput = executeRunConfiguration("myTask", isDebugServerProcess = true)
    assertThat(debugOutput)
      .contains("Configuration cache entry reused.")
      .doesNotContain("configuration cache cannot be reused")
      .doesNotContain("Configuration cache entry stored.")
      .contains("BUILD SUCCESSFUL")
      .contains(message)
  }

  @Test
  fun `daemon is started with debug flags only if script debugging is enabled`() {
    val argsFile = File(projectPath, "args.txt")

    importProject {
      withMavenCentral()
      applyPlugin("java")
      addPostfix("""
        import java.lang.management.ManagementFactory;
        import java.lang.management.RuntimeMXBean;
        
        task myTask {
          doFirst {
            RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
            List<String> arguments = runtimeMxBean.getInputArguments();
            File file = new File("${argsFile.systemIndependentPath}")
            file.write(arguments.toString())
          }
        }
      """.trimIndent())
    }

    ensureDeleted(argsFile)
    executeRunConfiguration("myTask", isDebugServerProcess = true)
    assertDebugJvmArgs(":myTask", argsFile)

    ensureDeleted(argsFile)
    executeRunConfiguration("myTask", isDebugServerProcess = false)
    assertDebugJvmArgs(":myTask", argsFile, shouldBeDebugged = false)
  }

  @Test
  fun `test tasks debugging for modules`() {
    createPrintArgsClass()
    createPrintArgsClass("module")

    val projectArgsFile = createArgsFile()
    val moduleArgsFile = createArgsFile("module")

    createSettingsFile { include("module") }
    createBuildFile("module") { withPrintArgsTask(moduleArgsFile) }
    importProject { withPrintArgsTask(projectArgsFile) }

    ensureDeleted(projectArgsFile, moduleArgsFile)
    executeRunConfiguration("printArgs")
    assertDebugJvmArgs(":printArgs", projectArgsFile)
    assertDebugJvmArgs(":module:printArgs", moduleArgsFile)

    ensureDeleted(projectArgsFile, moduleArgsFile)
    executeRunConfiguration(":printArgs")
    assertDebugJvmArgs(":printArgs", projectArgsFile)
    assertDebugJvmArgs(":module:printArgs", moduleArgsFile, shouldBeStarted = false)

    ensureDeleted(projectArgsFile, moduleArgsFile)
    executeRunConfiguration(":module:printArgs")
    assertDebugJvmArgs(":printArgs", projectArgsFile, shouldBeStarted = false)
    assertDebugJvmArgs(":module:printArgs", moduleArgsFile)

    ensureDeleted(projectArgsFile, moduleArgsFile)
    executeRunConfiguration("module:printArgs")
    assertDebugJvmArgs(":printArgs", projectArgsFile, shouldBeStarted = false)
    assertDebugJvmArgs(":module:printArgs", moduleArgsFile)

    ensureDeleted(projectArgsFile, moduleArgsFile)
    executeRunConfiguration(":printArgs", modulePath = "module")
    assertDebugJvmArgs(":printArgs", projectArgsFile)
    assertDebugJvmArgs(":module:printArgs", moduleArgsFile, shouldBeStarted = false)

    ensureDeleted(projectArgsFile, moduleArgsFile)
    executeRunConfiguration("printArgs", modulePath = "module")
    assertDebugJvmArgs(":printArgs", projectArgsFile, shouldBeStarted = false)
    assertDebugJvmArgs(":module:printArgs", moduleArgsFile)
  }

  @Test
  fun `test tasks debugging for module with dependent tasks`() {
    createPrintArgsClass()
    val implicitArgsFile = createArgsFile(name = "implicit-args.txt")
    val explicitArgsFile = createArgsFile(name = "explicit-args.txt")

    importProject {
      withPrintArgsTask(implicitArgsFile, "implicitTask")
      withPrintArgsTask(explicitArgsFile, "explicitTask", dependsOn = ":implicitTask")
    }

    ensureDeleted(implicitArgsFile, explicitArgsFile)
    executeRunConfiguration("explicitTask")
    assertDebugJvmArgs(":implicitTask", implicitArgsFile, shouldBeDebugged = false)
    assertDebugJvmArgs(":explicitTask", explicitArgsFile)

    ensureDeleted(implicitArgsFile, explicitArgsFile)
    executeRunConfiguration("explicitTask", isDebugAllEnabled = true)
    assertDebugJvmArgs(":implicitTask", implicitArgsFile)
    assertDebugJvmArgs(":explicitTask", explicitArgsFile)
  }

  @Test
  fun `test tasks debugging for module with dependent tasks with same name`() {
    createPrintArgsClass()
    createPrintArgsClass("module")

    val projectArgsFile = createArgsFile()
    val moduleArgsFile = createArgsFile("module")

    createSettingsFile { include("module") }
    createBuildFile("module") { withPrintArgsTask(moduleArgsFile, dependsOn = ":printArgs") }
    importProject { withPrintArgsTask(projectArgsFile) }

    ensureDeleted(projectArgsFile, moduleArgsFile)
    executeRunConfiguration("printArgs")
    assertDebugJvmArgs(":printArgs", projectArgsFile)
    assertDebugJvmArgs(":module:printArgs", moduleArgsFile)

    ensureDeleted(projectArgsFile, moduleArgsFile)
    executeRunConfiguration(":module:printArgs")
    assertDebugJvmArgs(":printArgs", projectArgsFile, shouldBeDebugged = false)
    assertDebugJvmArgs(":module:printArgs", moduleArgsFile)

    ensureDeleted(projectArgsFile, moduleArgsFile)
    executeRunConfiguration("module:printArgs")
    assertDebugJvmArgs(":printArgs", projectArgsFile, shouldBeDebugged = false)
    assertDebugJvmArgs(":module:printArgs", moduleArgsFile)

    ensureDeleted(projectArgsFile, moduleArgsFile)
    executeRunConfiguration(":printArgs", modulePath = "module")
    assertDebugJvmArgs(":printArgs", projectArgsFile)
    assertDebugJvmArgs(":module:printArgs", moduleArgsFile, shouldBeStarted = false)

    ensureDeleted(projectArgsFile, moduleArgsFile)
    executeRunConfiguration("printArgs", modulePath = "module")
    assertDebugJvmArgs(":printArgs", projectArgsFile, shouldBeDebugged = false)
    assertDebugJvmArgs(":module:printArgs", moduleArgsFile)
  }

  @Test
  fun `test tasks debugging for partial matched tasks`() {
    createPrintArgsClass()

    val projectArgsFile = createArgsFile()

    importProject { withPrintArgsTask(projectArgsFile) }

    ensureDeleted(projectArgsFile, projectArgsFile)
    executeRunConfiguration("print")
    assertDebugJvmArgs("printArgs", projectArgsFile)

    ensureDeleted(projectArgsFile, projectArgsFile)
    executeRunConfiguration(":print")
    assertDebugJvmArgs("printArgs", projectArgsFile)

    ensureDeleted(projectArgsFile, projectArgsFile)
    executeRunConfiguration("printArgs")
    assertDebugJvmArgs("printArgs", projectArgsFile)

    ensureDeleted(projectArgsFile, projectArgsFile)
    executeRunConfiguration(":printArgs")
    assertDebugJvmArgs("printArgs", projectArgsFile)
  }

  @Test
  fun `test tasks debugging for tasks that defined in script parameters`() {
    createPrintArgsClass()
    val projectArgsFile = createArgsFile()

    importProject { withPrintArgsTask(projectArgsFile) }

    ensureDeleted(projectArgsFile)
    executeRunConfiguration("printArgs", scriptParameters = "print")
    assertDebugJvmArgs("printArgs", projectArgsFile)

    ensureDeleted(projectArgsFile)
    executeRunConfiguration("printArgs", scriptParameters = ":print")
    assertDebugJvmArgs("printArgs", projectArgsFile)
  }

  @Test
  @TargetVersions("4.9+")
  fun `test tasks configuration avoidance during debug`() {
    createPrintArgsClass()
    val projectArgsFile = createArgsFile()

    importProject {
      withPrintArgsTask(projectArgsFile)
      registerTask("bomb") {
        call("println", "BOOM!")
      }
    }

    ensureDeleted(projectArgsFile)
    assertThat(executeRunConfiguration("printArgs"))
      .doesNotContain("BOOM!")
    assertDebugJvmArgs("printArgs", projectArgsFile)

    ensureDeleted(projectArgsFile)
    assertThat(executeRunConfiguration(":printArgs"))
      .doesNotContain("BOOM!")
    assertDebugJvmArgs("printArgs", projectArgsFile)

    ensureDeleted(projectArgsFile)
    assertThat(executeRunConfiguration("bomb"))
      .contains("BOOM!")
    assertDebugJvmArgs("printArgs", projectArgsFile, shouldBeStarted = false)
  }

  @Test
  fun `test tasks debugging for composite build`() {
    createPrintArgsClass()
    createPrintArgsClass("module")
    createPrintArgsClass("composite")
    createPrintArgsClass("composite/module")

    val projectArgsFile = createArgsFile()
    val moduleArgsFile = createArgsFile("module")
    val compositeArgsFile = createArgsFile("composite")
    val compositeModuleArgsFile = createArgsFile("composite/module")

    createSettingsFile("composite") { include("module") }
    createBuildFile("composite") { withPrintArgsTask(compositeArgsFile) }
    createBuildFile("composite/module") { withPrintArgsTask(compositeModuleArgsFile) }

    createSettingsFile {
      include("module")
      includeBuild("composite")
    }
    createBuildFile("module") { withPrintArgsTask(moduleArgsFile) }
    importProject { withPrintArgsTask(projectArgsFile) }

    ensureDeleted(projectArgsFile, moduleArgsFile, compositeArgsFile, compositeModuleArgsFile)
    executeRunConfiguration("printArgs")
    assertDebugJvmArgs(":printArgs", projectArgsFile)
    assertDebugJvmArgs(":module:printArgs", moduleArgsFile)
    assertDebugJvmArgs(":composite:printArgs", compositeArgsFile, shouldBeStarted = false)
    assertDebugJvmArgs(":composite:module:printArgs", compositeModuleArgsFile, shouldBeStarted = false)
  }

  /**
   * The test checks the legacy scenario when the task is executed with a non-root `modulePath`.
   * This means that the task is executed without the root project scope, as an independent of the root project task.
   * As a result, the nested composite project must have its own Gradle wrapper.
   */
  @Test
  fun `test composite tasks debugging for composite build`() {
    createPrintArgsClass()
    createPrintArgsClass("module")
    createPrintArgsClass("composite")
    createPrintArgsClass("composite/module")

    val projectArgsFile = createArgsFile()
    val moduleArgsFile = createArgsFile("module")
    val compositeArgsFile = createArgsFile("composite")
    val compositeModuleArgsFile = createArgsFile("composite/module")

    createSettingsFile("composite") { include("module") }
    createBuildFile("composite") { withPrintArgsTask(compositeArgsFile) }
    createBuildFile("composite/module") { withPrintArgsTask(compositeModuleArgsFile) }

    // we should explicitly create a wrapper config for the nested project
    createGradleWrapper("./composite")

    createSettingsFile {
      include("module")
      includeBuild("composite")
    }
    createBuildFile("module") { withPrintArgsTask(moduleArgsFile) }
    importProject { withPrintArgsTask(projectArgsFile) }

    if (isGradleAtLeast("6.9")) {
      ensureDeleted(projectArgsFile, moduleArgsFile, compositeArgsFile, compositeModuleArgsFile)
      executeRunConfiguration(":composite:printArgs")
      assertDebugJvmArgs(":printArgs", projectArgsFile, shouldBeStarted = false)
      assertDebugJvmArgs(":module:printArgs", moduleArgsFile, shouldBeStarted = false)
      assertDebugJvmArgs(":composite:printArgs", compositeArgsFile)
      assertDebugJvmArgs(":composite:module:printArgs", compositeModuleArgsFile, shouldBeStarted = false)

      ensureDeleted(projectArgsFile, moduleArgsFile, compositeArgsFile, compositeModuleArgsFile)
      executeRunConfiguration(":composite:module:printArgs")
      assertDebugJvmArgs(":printArgs", projectArgsFile, shouldBeStarted = false)
      assertDebugJvmArgs(":module:printArgs", moduleArgsFile, shouldBeStarted = false)
      assertDebugJvmArgs(":composite:printArgs", compositeArgsFile, shouldBeStarted = false)
      assertDebugJvmArgs(":composite:module:printArgs", compositeModuleArgsFile)
    }

    ensureDeleted(projectArgsFile, moduleArgsFile, compositeArgsFile, compositeModuleArgsFile)
    executeRunConfiguration("printArgs", modulePath = "composite")
    assertDebugJvmArgs(":printArgs", projectArgsFile, shouldBeStarted = false)
    assertDebugJvmArgs(":module:printArgs", moduleArgsFile, shouldBeStarted = false)
    assertDebugJvmArgs(":composite:printArgs", compositeArgsFile)
    assertDebugJvmArgs(":composite:module:printArgs", compositeModuleArgsFile)

    ensureDeleted(projectArgsFile, moduleArgsFile, compositeArgsFile, compositeModuleArgsFile)
    executeRunConfiguration(":printArgs", modulePath = "composite")
    assertDebugJvmArgs(":printArgs", projectArgsFile, shouldBeStarted = false)
    assertDebugJvmArgs(":module:printArgs", moduleArgsFile, shouldBeStarted = false)
    assertDebugJvmArgs(":composite:printArgs", compositeArgsFile)
    assertDebugJvmArgs(":composite:module:printArgs", compositeModuleArgsFile, shouldBeStarted = false)

    ensureDeleted(projectArgsFile, moduleArgsFile, compositeArgsFile, compositeModuleArgsFile)
    executeRunConfiguration(":module:printArgs", modulePath = "composite")
    assertDebugJvmArgs(":printArgs", projectArgsFile, shouldBeStarted = false)
    assertDebugJvmArgs(":module:printArgs", moduleArgsFile, shouldBeStarted = false)
    assertDebugJvmArgs(":composite:printArgs", compositeArgsFile, shouldBeStarted = false)
    assertDebugJvmArgs(":composite:module:printArgs", compositeModuleArgsFile)
  }

  @Test
  fun `test tasks debugging for test task`() {
    val jUnitTestAnnotationClass = when (isJunit5Supported(currentGradleVersion)) {
      true -> "org.junit.jupiter.api.Test"
      else -> "org.junit.Test"
    }
    createProjectSubFile("src/test/java/TestCase.java", """
      |import java.io.BufferedWriter;
      |import java.io.FileWriter;
      |import java.io.IOException;
      |import java.lang.management.ManagementFactory;
      |import java.lang.management.RuntimeMXBean;
      |import java.util.List;
      |
      |public class TestCase {
      |
      |  @$jUnitTestAnnotationClass
      |  public void test() {
      |    RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
      |    List<String> jvmArgs = runtimeMxBean.getInputArguments();
      |
      |    try (BufferedWriter os = new BufferedWriter(new FileWriter("args.txt", false))) {
      |      os.write(jvmArgs.toString());
      |    } catch (IOException e) {
      |      throw new RuntimeException(e);
      |    }
      |  }
      |}
    """.trimMargin())

    importProject {
      withJavaPlugin()
      withJUnit()
    }

    executeRunConfiguration(":test")
    assertDebugJvmArgs(":test", File(projectPath, "args.txt"))
  }
}