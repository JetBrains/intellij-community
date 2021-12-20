// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution

import com.intellij.openapi.util.io.systemIndependentPath
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.io.File

class GradleDebuggingIntegrationTest : GradleDebuggingIntegrationTestCase() {

  @Test
  fun `daemon is started with debug flags only if script debugging is enabled`() {
    val argsFile = File(projectPath, "args.txt")

    importProject {
      withMavenCentral()
      applyPlugin("'java'")
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
    executeRunConfiguration("myTask", isScriptDebugEnabled = true)
    assertDebugJvmArgs(":myTask", argsFile)

    ensureDeleted(argsFile)
    executeRunConfiguration("myTask", isScriptDebugEnabled = false)
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
  fun `test task configuration avoidance during debug`() {
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
  @TargetVersions("3.1+")
  fun `test task debugging for composite build`() {
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

    if (isGradleNewerOrSameAs("6.9")) {
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
}