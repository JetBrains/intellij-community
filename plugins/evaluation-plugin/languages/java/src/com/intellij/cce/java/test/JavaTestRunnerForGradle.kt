package com.intellij.cce.java.test

import com.intellij.cce.test.TestRunResult
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.DefaultJavaProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.service.task.GradleTaskManagerExtension

internal object JavaTestRunnerForGradle {
  @Suppress("TestOnlyProblems")
  suspend fun run(project: Project, moduleTests: List<ModuleTests>): TestRunResult {
    return Disposer.newDisposable().use { disposable ->
      // `KotlinWasmBrowserDebugGradleTaskManagerExtension` can break older gradle versions
      val testExtensions = GradleTaskManagerExtension.EP_NAME.extensionList.filterNot { it.javaClass.name.contains("Wasm") }
      (GradleTaskManagerExtension.EP_NAME.point as ExtensionPointImpl<GradleTaskManagerExtension>).maskAll(testExtensions, disposable, true)

      // Default gradle test console doesn't provide output to ProcessListener
      (ExternalSystemExecutionConsoleManager.EP_NAME.point as ExtensionPointImpl<ExternalSystemExecutionConsoleManager<*, *>>)
        .maskAll(listOf(), disposable, true)

      doRunTests(project, moduleTests)
    }
  }

  private suspend fun doRunTests(project: Project, moduleTests: List<ModuleTests>): TestRunResult {

    val runner = DefaultJavaProgramRunner.getInstance()
    val executor = DefaultRunExecutor.getRunExecutorInstance()
    val configSettings = configSettings(project, moduleTests)
    val environment = ExecutionEnvironment(executor, runner, configSettings, project)

    val results = RunConfigurationResults.compute { callback ->
      environment.callback = callback
      runner.execute(environment)
    }

    val allTests = mutableSetOf<String>()
    val failedTests = mutableSetOf<String>()
    val output = results.output.lines().joinToString("\n") { line ->
      if (line.contains("<event type='afterTest'>")) {
        val className = readAttribute(line, "className")
        val resultType = readAttribute(line, "resultType")
        val displayName = readAttribute(line, "displayName")
        val success = resultType == "SUCCESS"

        allTests.add(className)
        if (!success) {
          failedTests.add(className)
        }

        val highlightPrefix = if (success) " <<<++++<<< " else " <<<----<<< "

        line + "${highlightPrefix}[$resultType] $className:$displayName"
      }
      else line
    }

    return TestRunResult(results.exitCode, (allTests - failedTests).toList(), failedTests.toList(), true, true, output)
  }

  private fun configSettings(project: Project, moduleTests: List<ModuleTests>): RunnerAndConfigurationSettings {
    val settings = RunManager.getInstance(project).createConfiguration(
      "gradle-tests",
      ConfigurationTypeUtil.findConfigurationType(GradleExternalTaskConfigurationType::class.java).factory
    )
    val runConfiguration = settings.configuration as GradleRunConfiguration
    runConfiguration.isRunAsTest = true
    runConfiguration.settings.scriptParameters = moduleTests.joinToString(" ") { (moduleName, testNames) ->
      if (moduleName != null) ":$moduleName:test ${testNames.joinToString(" ") { "--tests $it" }}"
      else ":test ${testNames.joinToString(" ") { "--tests $it" }}"
    }
    return settings
  }

  private fun readAttribute(xml: String, name: String): String {
    val startTag = "$name='"
    val startIndex = xml.indexOf(startTag) + startTag.length
    val endIndex = xml.indexOf("'", startIndex)
    return if (startIndex >= startTag.length && endIndex > startIndex) {
      xml.substring(startIndex, endIndex)
    }
    else {
      throw IllegalArgumentException("Attribute '$name' not found in the given XML")
    }
  }
}