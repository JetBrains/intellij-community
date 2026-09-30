// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.coverage

import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.plugins.gradle.service.execution.loadInitScript
import org.jetbrains.plugins.gradle.service.execution.toGroovyListLiteral
import org.jetbrains.plugins.gradle.service.execution.toGroovyStringLiteral
import org.jetbrains.plugins.gradle.service.task.GradleTaskExecutionContext
import org.jetbrains.plugins.gradle.service.task.GradleTaskManagerExtension
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings

/**
 * Turns off the agent of the Gradle `jacoco` plugin, so that the IDE JaCoCo agent is the only JaCoCo agent of the test task.
 *
 * A coverage run with the JaCoCo runner adds the IDE JaCoCo agent to the test task.
 * A project that applies the Gradle `jacoco` plugin already has an agent on the same task.
 * Two JaCoCo agents in one JVM both define the class `java.lang.$JaCoCo`, and the JVM aborts.
 * See IDEA-269284.
 *
 * This extension adds an init script that sets `jacoco.enabled = false` for the test task.
 * The IDE agent then collects the data with the bundled JaCoCo version, which is also the version of the IDE reader.
 * The init script does nothing when the test task has no enabled `jacoco` extension.
 *
 * The extension does nothing for the IntelliJ coverage runner, whose agent does not clash.
 */
internal class GradleJaCoCoCoverageTaskManagerExtension : GradleTaskManagerExtension {

  override fun configureTasks(settings: GradleExecutionSettings, context: GradleTaskExecutionContext) {
    if (!hasJaCoCoAgent(settings.jvmParameters)) return
    settings.addInitScript(SCRIPT_NAME, loadJaCoCoCoverageInitScript(settings.tasks))
  }

  private fun loadJaCoCoCoverageInitScript(tasks: List<String>): String {
    return loadInitScript(javaClass, SCRIPT_PATH, mapOf(
      "TASKS" to tasks.toGroovyListLiteral { toGroovyStringLiteral() },
    ))
  }

  private companion object {
    private const val SCRIPT_NAME = "ijJaCoCoCoverage"
    private const val SCRIPT_PATH = "/com/intellij/gradle/java/coverage/JaCoCoCoverageInit.gradle"
  }
}

/**
 * Checks whether [jvmParameters] contain the IDE JaCoCo agent.
 *
 * The JaCoCo runner builds `-javaagent:<jar>=destfile=<file>,append=false[,includes=<a>:<b>]`.
 * The IntelliJ runner builds `-javaagent:<jar>=<options file>`, which carries no `destfile` option.
 * The `destfile` option therefore tells the two runners apart.
 */
internal fun hasJaCoCoAgent(jvmParameters: String?): Boolean {
  if (jvmParameters.isNullOrBlank()) return false
  for (parameter in ParametersListUtil.parse(jvmParameters)) {
    if (!parameter.startsWith(JAVA_AGENT_PREFIX)) continue
    val optionsIndex = parameter.indexOf('=')
    if (optionsIndex < 0) continue
    val hasDestinationFile = parameter.substring(optionsIndex + 1)
      .split(',')
      .any { it.startsWith(DESTINATION_FILE_OPTION_PREFIX) }
    if (hasDestinationFile) return true
  }
  return false
}

private const val JAVA_AGENT_PREFIX = "-javaagent:"
private const val DESTINATION_FILE_OPTION_PREFIX = "destfile="
