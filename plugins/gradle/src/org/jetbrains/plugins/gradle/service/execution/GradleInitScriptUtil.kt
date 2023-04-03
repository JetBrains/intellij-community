// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleInitScriptUtil")

package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.util.io.FileUtil
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.tooling.internal.init.Init
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineTask
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.regex.Matcher


const val MAIN_INIT_SCRIPT_NAME = "ijInit"
const val MAPPER_INIT_SCRIPT_NAME = "ijMapper"
const val WRAPPER_INIT_SCRIPT_NAME = "ijWrapper"
const val TEST_INIT_SCRIPT_NAME = "ijTestInit"

fun createMainInitScript(isBuildSrcProject: Boolean, toolingExtensionClasses: Set<Class<*>>): File {
  val jarPaths = GradleExecutionHelper.getToolingExtensionsJarPaths(toolingExtensionClasses)
  val initScript = loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/Init.gradle", mapOf(
    "EXTENSIONS_JARS_PATH" to jarPaths.toGroovyListLiteral { "mapPath(" + toGroovyStringLiteral() + ")" },
    "IS_BUILD_SCR_PROJECT" to isBuildSrcProject.toString()
  ))
  return createInitScript(MAIN_INIT_SCRIPT_NAME, initScript)
}

fun loadTaskInitScript(
  projectPath: String,
  taskName: String,
  taskType: String,
  toolingExtensionClasses: Set<Class<*>>,
  taskConfiguration: String?
): String {
  val jarPaths = GradleExecutionHelper.getToolingExtensionsJarPaths(toolingExtensionClasses)
  return loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/TaskInit.gradle", mapOf(
    "EXTENSIONS_JARS_PATH" to jarPaths.toGroovyListLiteral { "mapPath(" + toGroovyStringLiteral() + ")" },
    "PROJECT_PATH" to projectPath.toGroovyStringLiteral(),
    "TASK_NAME" to taskName.toGroovyStringLiteral(),
    "TASK_TYPE" to taskType,
    "TASK_CONFIGURATION" to (taskConfiguration ?: "")
  ))
}

fun createTargetPathMapperInitScript(): File {
  val initScript = loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/MapperInit.gradle")
  return createInitScript(MAPPER_INIT_SCRIPT_NAME, initScript)
}

fun createWrapperInitScript(
  gradleVersion: GradleVersion?,
  jarFile: File,
  scriptFile: File,
  fileWithPathToProperties: File
): File {
  val initScript = loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/WrapperInit.gradle", mapOf(
    "GRADLE_VERSION" to (gradleVersion?.version?.toGroovyStringLiteral() ?: "null"),
    "JAR_FILE" to jarFile.path.toGroovyStringLiteral(),
    "SCRIPT_FILE" to scriptFile.path.toGroovyStringLiteral(),
    "FILE_WITH_PATH_TO_PROPERTIES" to fileWithPathToProperties.path.toGroovyStringLiteral()
  ))
  return createInitScript(WRAPPER_INIT_SCRIPT_NAME, initScript)
}

fun createTestInitScript(tasks: List<GradleCommandLineTask>): File {
  val initScript = loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/TestInit.gradle", mapOf(
    "IMPORT_GRADLE_TASKS_UTIL" to loadInitScript(
      "/org/jetbrains/plugins/gradle/tooling/internal/init/GradleTasksUtil.gradle"),
    "TEST_TASKS_WITH_PATTERNS" to tasks.associate { it.name to it.getTestPatterns() }
      .toGroovyMapLiteral({ toGroovyStringLiteral() }, { toGroovyListLiteral { toGroovyStringLiteral() } })
  ))
  return createInitScript(TEST_INIT_SCRIPT_NAME, initScript)
}

fun loadJvmDebugInitScript(
  debuggerId: String,
  parameters: String
): String {
  return loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/JvmDebugInit.gradle", java.util.Map.of(
    "IMPORT_GRADLE_TASKS_UTIL", loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/GradleTasksUtil.gradle"),
    "DEBUGGER_ID", debuggerId.toGroovyStringLiteral(),
    "PROCESS_PARAMETERS", parameters.toGroovyStringLiteral()
  ))
}

private fun loadInitScript(resourcePath: String, parameters: Map<String, String>): String {
  var script = loadInitScript(resourcePath)
  for ((key, value) in parameters) {
    val replacement = Matcher.quoteReplacement(value)
    script = script.replaceFirst(key.toRegex(), replacement)
  }
  return script
}

private fun loadInitScript(resourcePath: String): String {
  val resource = Init::class.java.getResource(resourcePath)
  if (resource == null) {
    throw IllegalArgumentException("Cannot find init file $resourcePath")
  }
  try {
    return resource.readText()
  }
  catch (e: IOException) {
    throw IllegalStateException("Cannot read init file $resourcePath", e)
  }
}

fun createInitScript(prefix: String, content: String): File {
  val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
  val contentLength = contentBytes.size
  return FileUtil.findSequentFile(File(FileUtil.getTempDirectory()), prefix, GradleConstants.EXTENSION) { file: File ->
    try {
      if (!file.exists()) {
        FileUtil.writeToFile(file, contentBytes, false)
        @Suppress("SSBasedInspection")
        file.deleteOnExit()
        return@findSequentFile true
      }
      if (contentLength.toLong() != file.length()) return@findSequentFile false
      return@findSequentFile content == FileUtil.loadFile(file, StandardCharsets.UTF_8)
    }
    catch (ignore: IOException) {
      // Skip file with access issues. Will attempt to check the next file
    }
    false
  }
}
