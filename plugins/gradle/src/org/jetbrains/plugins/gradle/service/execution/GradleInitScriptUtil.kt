// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleInitScriptUtil")

package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.util.io.FileUtil
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.tooling.internal.init.Init
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.util.regex.Matcher
import kotlin.io.path.*

const val MAIN_INIT_SCRIPT_NAME = "ijInit"
const val MAPPER_INIT_SCRIPT_NAME = "ijMapper"
const val WRAPPER_INIT_SCRIPT_NAME = "ijWrapper"
const val TEST_INIT_SCRIPT_NAME = "ijTestInit"
const val IDEA_PLUGIN_CONFIGURATOR_SCRIPT_NAME = "ijIdeaPluginConfigurator"

fun createMainInitScript(isBuildSrcProject: Boolean, toolingExtensionClasses: Set<Class<*>>): Path {
  val jarPaths = GradleExecutionHelper.getToolingExtensionsJarPaths(toolingExtensionClasses)
  val initScript = joinInitScripts(
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/RegistryProcessor.gradle"),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/JetGradlePlugin.gradle"),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/Init.gradle", mapOf(
      "EXTENSIONS_JARS_PATH" to jarPaths.toGroovyListLiteral { "mapPath(" + toGroovyStringLiteral() + ")" },
      "IS_BUILD_SCR_PROJECT" to isBuildSrcProject.toString()
    ))
  )
  return createInitScript(MAIN_INIT_SCRIPT_NAME, initScript)
}

fun createIdeaPluginConfiguratorInitScript() : Path {
  val initScript = loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/IdeaPluginConfigurator.gradle")
  return createInitScript(IDEA_PLUGIN_CONFIGURATOR_SCRIPT_NAME, initScript)
}

fun loadDownloadSourcesInitScript(dependencyNotation: String,
                                  taskName: String,
                                  downloadTarget: String): String = loadDownloadSourcesInitScript(
  "/org/jetbrains/plugins/gradle/tooling/internal/init/downloadSources.gradle", dependencyNotation, taskName, downloadTarget)

fun loadLegacyDownloadSourcesInitScript(dependencyNotation: String,
                                        taskName: String,
                                        downloadTarget: String): String = loadDownloadSourcesInitScript(
  "/org/jetbrains/plugins/gradle/tooling/internal/init/legacyDownloadSources.gradle", dependencyNotation,
  taskName, downloadTarget)

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

fun createTargetPathMapperInitScript(): Path {
  val initScript = loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/MapperInit.gradle")
  return createInitScript(MAPPER_INIT_SCRIPT_NAME, initScript)
}

fun createWrapperInitScript(
  gradleVersion: GradleVersion?,
  jarFile: File,
  scriptFile: File,
  fileWithPathToProperties: File
): Path {
  val initScript = loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/WrapperInit.gradle", mapOf(
    "GRADLE_VERSION" to (gradleVersion?.version?.toGroovyStringLiteral() ?: "null"),
    "JAR_FILE" to jarFile.path.toGroovyStringLiteral(),
    "SCRIPT_FILE" to scriptFile.path.toGroovyStringLiteral(),
    "FILE_WITH_PATH_TO_PROPERTIES" to fileWithPathToProperties.path.toGroovyStringLiteral()
  ))
  return createInitScript(WRAPPER_INIT_SCRIPT_NAME, initScript)
}

fun createTestInitScript(): Path {
  val initScript = joinInitScripts(
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/TestInit.gradle")
  )
  return createInitScript(TEST_INIT_SCRIPT_NAME, initScript)
}

fun loadJvmDebugInitScript(
  debuggerId: String,
  parameters: String
): String {
  return joinInitScripts(
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/GradleTasksUtil.gradle"),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/JvmDebugInit.gradle", mapOf(
      "DEBUGGER_ID" to debuggerId.toGroovyStringLiteral(),
      "PROCESS_PARAMETERS" to parameters.toGroovyStringLiteral()
    ))
  )
}

private val JUNIT_3_COMPARISON_FAILURE = listOf("junit.framework.ComparisonFailure")
private val JUNIT_4_COMPARISON_FAILURE = listOf("org.junit.ComparisonFailure")
private val ASSERTION_FAILED_ERROR = listOf("org.opentest4j.AssertionFailedError")
private val FILE_COMPARISON_FAILURE = listOf("com.intellij.rt.execution.junit.FileComparisonFailure",
                                             "junit.framework.ComparisonFailure")

fun loadIjTestLoggerInitScript(): String {
  return joinInitScripts(
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/TestEventLogger.gradle"),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/IjTestEventLogger.gradle"),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/IjTestEventLoggerInit.gradle"),
    loadEnhanceGradleDaemonClasspathInit(listOf(
      JUNIT_3_COMPARISON_FAILURE,
      JUNIT_4_COMPARISON_FAILURE,
      ASSERTION_FAILED_ERROR,
      FILE_COMPARISON_FAILURE
    ))
  )
}

fun loadFileComparisonTestLoggerInitScript(): String {
  return joinInitScripts(
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/TestEventLogger.gradle"),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/FileComparisonTestEventLogger.gradle"),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/FileComparisonTestEventLoggerInit.gradle"),
    loadEnhanceGradleDaemonClasspathInit(listOf(FILE_COMPARISON_FAILURE))
  )
}

/**
 * @param classesNames is list of classes groups.
 * Where a class group represents a class with its dependent classes.
 */
private fun loadEnhanceGradleDaemonClasspathInit(classesNames: List<List<String>>): String {
  return loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/EnhanceGradleDaemonClasspathInit.gradle", mapOf(
    "CLASS_NAMES" to classesNames.toGroovyListLiteral { toGroovyListLiteral { toGroovyStringLiteral() } }
  ))
}

private fun joinInitScripts(vararg initScripts: String): String {
  return initScripts.joinToString(System.lineSeparator())
}

private fun loadInitScript(resourcePath: String, parameters: Map<String, String> = emptyMap()): String {
  return loadInitScript(Init::class.java, resourcePath, parameters)
}

private fun loadInitScript(aClass: Class<*>, resourcePath: String, parameters: Map<String, String>): String {
  var script = loadInitScript(aClass, resourcePath)
  for ((key, value) in parameters) {
    val replacement = Matcher.quoteReplacement(value)
    script = script.replaceFirst(key.toRegex(), replacement)
  }
  return script
}

private fun loadInitScript(aClass: Class<*>, resourcePath: String): String {
  val resource = aClass.getResource(resourcePath)
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

fun createInitScript(prefix: String, content: String): Path {
  val contentBytes = content.encodeToByteArray()
  val tempDirectory = Path.of(FileUtil.getTempDirectory())
  tempDirectory.createDirectories()
  var suffix = 0
  while (true) {
    suffix++
    val candidateName = prefix + suffix + "." + GradleConstants.EXTENSION
    val candidate = tempDirectory.resolve(candidateName)
    try {
      candidate.createFile()
      candidate.writeBytes(contentBytes)
      @Suppress("SSBasedInspection")
      candidate.toFile().deleteOnExit()
      return candidate
    }
    catch (_: FileAlreadyExistsException) {
    }
    if (isContentEquals(candidate, contentBytes)) {
      return candidate
    }
  }
}

private fun isContentEquals(path: Path, content: ByteArray): Boolean {
  return content.size.toLong() == path.fileSize() &&
         content.contentEquals(path.readBytes())
}

private fun loadDownloadSourcesInitScript(path: String, dependencyNotation: String, taskName: String, downloadTarget: String): String {
  return loadInitScript(path, mapOf(
    "DEPENDENCY_NOTATION" to dependencyNotation.toGroovyStringLiteral(),
    "TARGET_PATH" to downloadTarget.toGroovyStringLiteral(),
    "GRADLE_TASK_NAME" to taskName.toGroovyStringLiteral()
  ))
}
