// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemProcessHandler
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemTaskDebugRunner
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.systemIndependentPath
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.importing.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder.Companion.buildscript
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GradleDebuggingIntegrationTest : GradleImportingTestCase() {

  private val debugString = "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address="

  @Test
  fun `daemon is started with debug flags only if script debugging is enabled`() {
    importProject(
      createBuildScriptBuilder()
        .withMavenCentral()
        .applyPlugin("'java'")
        .addPostfix("""
          import java.lang.management.ManagementFactory;
          import java.lang.management.RuntimeMXBean;
          
          task myTask {
            doFirst {
              RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
              List<String> arguments = runtimeMxBean.getInputArguments();
              File file = new File("args.txt")
              file.write(arguments.toString())
            }
          }
        """.trimIndent())
        .generate()
    )

    val gradleRC = createEmptyGradleRunConfiguration("myRC")
    gradleRC.settings.apply {
      externalProjectPath = projectPath
      taskNames = listOf("myTask")
    }
    gradleRC.isScriptDebugEnabled = true

    executeRunConfiguration(gradleRC)

    val reportFile = File(projectPath, "args.txt")
    assertTrue(reportFile.exists())
    assertTrue(reportFile.readText().contains(debugString))

    reportFile.delete()

    gradleRC.isScriptDebugEnabled = false
    executeRunConfiguration(gradleRC)

    assertTrue(reportFile.exists())
    assertFalse(reportFile.readText().contains(debugString))
  }


  private val jvmArgsPrinter = """
        package pack;
        import java.io.BufferedWriter;
        import java.io.FileWriter;
        import java.io.File;
        import java.lang.management.ManagementFactory;
        import java.lang.management.RuntimeMXBean;
        import java.util.List;
        import java.util.Arrays;
        
        public class AClass {
          public static void main(String[] args) {
            System.out.println(Arrays.toString(args));
            File file = new File(args[0]);
            BufferedWriter os = null;
            try {
              os = new BufferedWriter(new FileWriter(file, false));
              RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
              List<String> arguments = runtimeMxBean.getInputArguments();
              os.write(arguments.toString());
            } catch (Exception e) {
              e.printStackTrace();
            } finally {
              try {
                os.close();
              } catch (Exception e1) {
                e1.printStackTrace();
              }
            }
          }
        }
      """.trimIndent()

  @Test
  fun `only explicitly called tasks are debugged by default`() {

    createProjectSubFile("src/main/java/pack/AClass.java", jvmArgsPrinter)

    val implicitArgsFileName = "implicitTaskArgs.txt"
    val explicitArgsFileName = "explicitTaskArgs.txt"

    importProject(
      createBuildScriptBuilder()
        .withMavenCentral()
        .applyPlugin("'java'")
        .addPostfix("""
                              
          task implicitTask(type: JavaExec) {
            classpath = sourceSets.main.runtimeClasspath
            main = 'pack.AClass'
            args '$implicitArgsFileName'
          }
          
          task explicitTask(dependsOn: implicitTask, type: JavaExec) {
            classpath = sourceSets.main.runtimeClasspath
            main = 'pack.AClass'
            args '$explicitArgsFileName'
          }
        """.trimIndent())
        .generate()
    )

    val gradleRC = createEmptyGradleRunConfiguration("myRC")
    gradleRC.settings.apply {
      externalProjectPath = projectPath
      taskNames = listOf("explicitTask")
    }
    gradleRC.isScriptDebugEnabled = false

    val implicitArgsFile = File(projectPath, implicitArgsFileName)
    val explicitArgsFile = File(projectPath, explicitArgsFileName)

    assertThat(implicitArgsFile).doesNotExist()
    assertThat(explicitArgsFile).doesNotExist()

    executeRunConfiguration(gradleRC)

    assertThat(implicitArgsFile.readText()).describedAs("Task 'implicitTask' should not be debugged").doesNotContain(debugString)
    assertThat(explicitArgsFile.readText()).describedAs("Task 'explicitTask' should be debugged").contains(debugString)

    gradleRC.isDebugAllEnabled = true // also check that debugging of all supported tasks in tasks graph works

    implicitArgsFile.delete()
    explicitArgsFile.delete()

    executeRunConfiguration(gradleRC)

    assertThat(implicitArgsFile.readText())
      .describedAs("Task 'implicitTask' should be debugged when debug all is set")
      .contains(debugString)

    assertThat(explicitArgsFile.readText())
      .describedAs("Task 'explicitTask' should be debugged when debug all is set")
      .contains(debugString)
  }



  @Test
  fun `only explicit tasks are debugged for a gradle subproject`() {

    createProjectSubFile("src/main/java/pack/AClass.java", jvmArgsPrinter.trimIndent())

    createSettingsFile { include("subproject") }
    createProjectSubDir("subproject")

    val argsFileName = "args.txt"

    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .withMavenCentral()
        .addPostfix("""
          
          task printArgs(type: JavaExec) {
            classpath = sourceSets.main.runtimeClasspath
            main = 'pack.AClass'
            args '$argsFileName'
          }
          
          def rootProject = project
          
          project("subproject") {
            task printArgs(dependsOn: ":printArgs", type: JavaExec) {
              classpath = rootProject.sourceSets.main.runtimeClasspath
              main = 'pack.AClass'
              args '$argsFileName'
            }
          }
        """.trimIndent())
        .generate()
    )

    val gradleRC = createEmptyGradleRunConfiguration("myRC")
    val subProjectPath = "$projectPath/subproject"
    gradleRC.settings.apply {
      externalProjectPath = subProjectPath
      taskNames = listOf("printArgs")
    }
    gradleRC.isScriptDebugEnabled = false

    val rootArgsFile = File(projectPath, argsFileName)
    val subProjectArgsFile = File(subProjectPath, argsFileName)

    assertThat(rootArgsFile).doesNotExist()
    assertThat(subProjectArgsFile).doesNotExist()

    executeRunConfiguration(gradleRC)

    assertThat(rootArgsFile.readText()).describedAs("root task should not be debugged").doesNotContain(debugString)
    assertThat(subProjectArgsFile.readText()).describedAs("sub project task should be debugged").contains(debugString)
  }


  @Test
  fun `explicit tasks are debugged for a gradle subproject when called relatively`() {

    createProjectSubFile("src/main/java/pack/AClass.java", jvmArgsPrinter.trimIndent())
    createSettingsFile { include("subproject") }
    createProjectSubDir("subproject")

    val argsFileName = "args.txt"

    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .withMavenCentral()
        .addPostfix("""          
          def rootProject = project
          
          project("subproject") {
            task printArgs(type: JavaExec) {
              classpath = rootProject.sourceSets.main.runtimeClasspath
              main = 'pack.AClass'
              args '$argsFileName'
            }
          }
        """.trimIndent())
        .generate()
    )

    val gradleRC = createEmptyGradleRunConfiguration("myRC")
    gradleRC.settings.apply {
      externalProjectPath = projectPath
      taskNames = listOf("printArgs")
    }
    gradleRC.isScriptDebugEnabled = false

    val subProjectArgsFile = File("$projectPath/subproject", argsFileName)
    assertThat(subProjectArgsFile).doesNotExist()

    executeRunConfiguration(gradleRC)

    assertThat(subProjectArgsFile.readText()).describedAs("sub project task should be debugged").contains(debugString)
  }

  @Test
  fun `test debug started for task with script parameters`() {
    createProjectSubFile("src/main/java/pack/AClass.java", jvmArgsPrinter)

    val subProjectArgsFile = File(projectPath, "args.txt")

    importProject {
      withJavaPlugin()
      withMavenCentral()
      withTask("simple")
      withTask("printArgs", "JavaExec") {
        assign("classpath", code("rootProject.sourceSets.main.runtimeClasspath"))
        assign("main", "pack.AClass")
        call("args", subProjectArgsFile.systemIndependentPath)
      }
    }

    assertThat(subProjectArgsFile)
      .doesNotExist()

    executeRunConfiguration(
      createEmptyGradleRunConfiguration("myRC").apply {
        isScriptDebugEnabled = false
        settings.apply {
          externalProjectPath = projectPath
          taskNames = listOf("printArgs")
          scriptParameters = "print"
        }
      }
    )

    assertThat(subProjectArgsFile.readText())
      .describedAs("Project task 'printArgs' should be debugged")
      .contains(debugString)

    subProjectArgsFile.delete()

    executeRunConfiguration(
      createEmptyGradleRunConfiguration("myRC").apply {
        isScriptDebugEnabled = false
        settings.apply {
          externalProjectPath = projectPath
          taskNames = listOf("printArgs")
          scriptParameters = ":print"
        }
      }
    )

    assertThat(subProjectArgsFile.readText())
      .describedAs("Project task 'printArgs' should be debugged")
      .contains(debugString)
  }

  @Test
  fun `test debug all tasks with same name`() {
    createProjectSubFile("src/main/java/pack/AClass.java", jvmArgsPrinter)
    createProjectSubFile("module/src/main/java/pack/AClass.java", jvmArgsPrinter)

    val subProjectArgs1File = File(projectPath, "args1.txt")
    val subProjectArgs2File = File(projectPath, "args2.txt")

    createSettingsFile {
      include("module")
    }
    createBuildFile("module/build.gradle") {
      withJavaPlugin()
      withTask("printArgs", "JavaExec") {
        assign("classpath", code("rootProject.sourceSets.main.runtimeClasspath"))
        assign("main", "pack.AClass")
        call("args", subProjectArgs1File.systemIndependentPath)
      }
    }
    importProject {
      withJavaPlugin()
      withTask("printArgs", "JavaExec") {
        assign("classpath", code("rootProject.sourceSets.main.runtimeClasspath"))
        assign("main", "pack.AClass")
        call("args", subProjectArgs2File.systemIndependentPath)
      }
    }

    executeRunConfiguration(
      createEmptyGradleRunConfiguration("myRC").apply {
        isScriptDebugEnabled = false
        settings.apply {
          externalProjectPath = projectPath
          taskNames = listOf("printArgs")
          scriptParameters = ""
        }
      }
    )

    assertDebugJvmArgs(":printArgs", subProjectArgs1File)
    assertDebugJvmArgs(":module:printArgs", subProjectArgs2File)
  }

  fun createSettingsFile(configure: GradleSettingScriptBuilder.() -> Unit) {
    createSettingsFile(GradleSettingScriptBuilder.settingsScript("project", configure))
  }

  fun createBuildFile(relativePath: String, configure: TestGradleBuildScriptBuilder.() -> Unit) {
    createProjectSubFile(relativePath, buildscript(configure))
  }

  fun importProject(configure: TestGradleBuildScriptBuilder.() -> Unit) {
    importProject(buildscript(configure))
  }

  fun assertDebugJvmArgs(printArgsTaskName: String, subProjectArgsFile: File, shouldBeDebugged: Boolean = true) {
    assertThat(subProjectArgsFile)
      .describedAs("Task '$printArgsTaskName' should be started")
      .exists()
      .content()
      .apply {
        if (shouldBeDebugged) {
          describedAs("Task '$printArgsTaskName' should be debugged")
            .contains(debugString)
        }
        else {
          describedAs("Task '$printArgsTaskName' should not be debugged")
            .doesNotContain(debugString)
        }
      }
  }

  private fun executeRunConfiguration(gradleRC: GradleRunConfiguration) {
    val executor = DefaultDebugExecutor.getDebugExecutorInstance()
    val runner = ExternalSystemTaskDebugRunner()
    val latch = CountDownLatch(1)
    val esHandler: AtomicReference<ExternalSystemProcessHandler> = AtomicReference()
    val env = ExecutionEnvironmentBuilder.create(executor, gradleRC)
      .build(ProgramRunner.Callback {
        val processHandler = it.processHandler as ExternalSystemProcessHandler
        processHandler.addProcessListener(object : ProcessAdapter() {
          override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            print(event.text)
          }
        })
        esHandler.set(processHandler)
        latch.countDown()
      })

    runInEdt {
      runner.execute(env)
    }

    latch.await(1, TimeUnit.MINUTES)
    esHandler.get().waitFor(TimeUnit.MINUTES.toMillis(1))
  }
}