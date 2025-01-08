// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemProcessHandler
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemTaskDebugRunner
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.testFramework.util.ExternalSystemExecutionTracer
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

abstract class GradleDebuggingIntegrationTestCase : GradleImportingTestCase() {

  private val debugString = "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address="

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

  fun createPrintArgsClass(relativeModulePath: String = ".") {
    createProjectSubFile("$relativeModulePath/src/main/java/pack/AClass.java", jvmArgsPrinter)
  }

  fun createArgsFile(relativeModulePath: String = ".", name: String = "args.txt"): File {
    return Path.of(projectPath).resolve(relativeModulePath).resolve(name).normalize().toFile()
  }

  fun TestGradleBuildScriptBuilder.withPrintArgsTask(
    argsFile: File,
    name: String = "printArgs",
    dependsOn: String? = null,
  ) {
    withJavaPlugin()
    withTask(name, "JavaExec") {
      if (dependsOn != null) {
        call("dependsOn", dependsOn)
      }
      assign("classpath", code("rootProject.sourceSets.main.runtimeClasspath"))
      if (GradleVersionUtil.isGradleAtLeast(gradleVersion, "7.0")) {
        assign("mainClass", "pack.AClass")
      } else {
        assign("main", "pack.AClass")
      }
      call("args", argsFile.systemIndependentPath)
    }
  }

  fun assertDebugJvmArgs(
    printArgsTaskName: String,
    argsFile: File,
    shouldBeStarted: Boolean = true,
    shouldBeDebugged: Boolean = shouldBeStarted
  ) {
    val fileAssertion = assertThat(argsFile)
    if (shouldBeStarted) {
      fileAssertion
        .describedAs("Task '$printArgsTaskName' should be started")
        .exists()
    }
    else {
      fileAssertion
        .describedAs("Task '$printArgsTaskName' should not be started")
        .doesNotExist()
      return
    }
    val contentAssertion = fileAssertion.content()
    if (shouldBeDebugged) {
      contentAssertion
        .describedAs("Task '$printArgsTaskName' should be debugged")
        .contains(debugString)
    }
    else {
      contentAssertion
        .describedAs("Task '$printArgsTaskName' should not be debugged")
        .doesNotContain(debugString)
    }
  }

  fun ensureDeleted(vararg files: File) {
    for (file in files) {
      FileUtil.delete(file)
    }
  }

  fun createGradlePropertiesFile(relativeModulePath: String = ".", content: String) {
    createProjectSubFile("$relativeModulePath/gradle.properties", content)
  }

  fun executeRunConfiguration(
    vararg taskNames: String,
    modulePath: String = ".",
    scriptParameters: String = "",
    isDebugServerProcess: Boolean = false,
    isDebugAllEnabled: Boolean = false
  ): String {
    val runConfiguration = createEmptyGradleRunConfiguration("run-configuration")
    runConfiguration.isDebugServerProcess = isDebugServerProcess
    runConfiguration.isDebugAllEnabled = isDebugAllEnabled
    runConfiguration.settings.externalProjectPath = FileUtil.toCanonicalPath("$projectPath/$modulePath")
    runConfiguration.settings.taskNames = taskNames.toList()
    runConfiguration.settings.scriptParameters = scriptParameters

    val tracker = ExternalSystemExecutionTracer()
    tracker.traceExecution {
      executeRunConfiguration(runConfiguration)
    }
    return tracker.output.joinToString("")
  }

  private fun executeRunConfiguration(runConfiguration: GradleRunConfiguration) {
    val executor = DefaultDebugExecutor.getDebugExecutorInstance()
    val runner = ExternalSystemTaskDebugRunner()
    val latch = CountDownLatch(1)
    val esHandler = AtomicReference<ExternalSystemProcessHandler>()
    val callback = ProgramRunner.Callback {
      esHandler.set(it.processHandler as ExternalSystemProcessHandler)
      latch.countDown()
    }
    val env = ExecutionEnvironmentBuilder.create(executor, runConfiguration)
      .build(callback)

    runInEdt {
      runner.execute(env)
    }

    latch.await(1, TimeUnit.MINUTES)
    esHandler.get().waitFor(TimeUnit.MINUTES.toMillis(1))
  }
}