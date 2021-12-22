// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemProcessHandler
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemTaskDebugRunner
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.systemIndependentPath
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import java.io.File
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
    return File(projectPath + File.separatorChar + relativeModulePath + File.separatorChar + name)
  }

  fun TestGradleBuildScriptBuilder.withPrintArgsTask(
    argsFile: File,
    name: String = "printArgs",
    dependsOn: String? = null,
  ) {
    withJavaPlugin()
    withTask(name, "JavaExec", dependsOn) {
      assign("classpath", code("rootProject.sourceSets.main.runtimeClasspath"))
      assign("main", "pack.AClass")
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

  fun executeRunConfiguration(
    vararg taskNames: String,
    modulePath: String = ".",
    scriptParameters: String = "",
    isScriptDebugEnabled: Boolean = false,
    isDebugAllEnabled: Boolean = false
  ): String {
    val runConfiguration = createEmptyGradleRunConfiguration("run-configuration")
    runConfiguration.isScriptDebugEnabled = isScriptDebugEnabled
    runConfiguration.isDebugAllEnabled = isDebugAllEnabled
    runConfiguration.settings.externalProjectPath = FileUtil.toCanonicalPath("$projectPath/$modulePath")
    runConfiguration.settings.taskNames = taskNames.toList()
    runConfiguration.settings.scriptParameters = scriptParameters

    val output = StringBuilder()
    executeRunConfiguration(runConfiguration) { text, outType ->
      val stream = if (ProcessOutputType.isStderr(outType)) System.err else System.out
      stream.print(text)
      output.append(text)
    }
    return output.toString()
  }

  private fun executeRunConfiguration(runConfiguration: GradleRunConfiguration, textConsumer: (String, Key<*>) -> Unit) {
    val executor = DefaultDebugExecutor.getDebugExecutorInstance()
    val runner = ExternalSystemTaskDebugRunner()
    val latch = CountDownLatch(1)
    val esHandler: AtomicReference<ExternalSystemProcessHandler> = AtomicReference()
    val env = ExecutionEnvironmentBuilder.create(executor, runConfiguration)
      .build(ProgramRunner.Callback {
        val processHandler = it.processHandler as ExternalSystemProcessHandler
        processHandler.addProcessListener(object : ProcessAdapter() {
          override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            textConsumer(event.text, outputType)
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