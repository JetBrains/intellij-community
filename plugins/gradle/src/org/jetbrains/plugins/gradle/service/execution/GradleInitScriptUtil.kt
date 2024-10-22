// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleInitScriptUtil")

package org.jetbrains.plugins.gradle.service.execution

import com.intellij.gradle.toolingExtension.GradleToolingExtensionClass
import com.intellij.gradle.toolingExtension.impl.GradleToolingExtensionImplClass
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.util.containers.ContainerUtil
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.tooling.internal.init.Init
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.util.regex.Matcher
import kotlin.io.path.*

private val LOG = Logger.getInstance("org.jetbrains.plugins.gradle.service.execution.GradleInitScriptUtil")
private val EXCLUDED_JAR_SUFFIXES = setOf(
  "lib/app.jar",
  "lib/app-client.jar",
  "lib/lib-client.jar"
)

const val MAIN_INIT_SCRIPT_NAME = "ijInit"
const val MAPPER_INIT_SCRIPT_NAME = "ijMapper"
const val WRAPPER_INIT_SCRIPT_NAME = "ijWrapper"
const val TEST_INIT_SCRIPT_NAME = "ijTestInit"
const val IDEA_PLUGIN_CONFIGURATOR_SCRIPT_NAME = "ijIdeaPluginConfigurator"

fun createMainInitScript(isBuildSrcProject: Boolean, toolingExtensionClasses: Set<Class<*>>): Path {
  val initScript = joinInitScripts(
    loadToolingExtensionProvidingInitScript(toolingExtensionClasses),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/RegistryProcessor.gradle"),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/JetGradlePlugin.gradle"),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/Init.gradle", mapOf(
      "IS_BUILD_SCR_PROJECT" to isBuildSrcProject.toString()
    ))
  )
  return createInitScript(MAIN_INIT_SCRIPT_NAME, initScript)
}

fun createIdeaPluginConfiguratorInitScript() : Path {
  val initScript = loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/IdeaPluginConfigurator.gradle")
  return createInitScript(IDEA_PLUGIN_CONFIGURATOR_SCRIPT_NAME, initScript)
}

fun loadDownloadSourcesInitScript(
  dependencyNotation: String,
  taskName: String,
  downloadTarget: Path,
  externalProjectPath: String,
): String {
  return loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/downloadSources.gradle", mapOf(
    "DEPENDENCY_NOTATION" to dependencyNotation.toGroovyStringLiteral(),
    "TARGET_PATH" to downloadTarget.toCanonicalPath().toGroovyStringLiteral(),
    "GRADLE_TASK_NAME" to taskName.toGroovyStringLiteral(),
    "GRADLE_PROJECT_PATH" to externalProjectPath.toGroovyStringLiteral(),
  ))
}

fun loadLegacyDownloadSourcesInitScript(
  dependencyNotation: String,
  taskName: String,
  downloadTarget: Path,
  externalProjectPath: String,
): String {
  return loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/legacyDownloadSources.gradle", mapOf(
    "DEPENDENCY_NOTATION" to dependencyNotation.toGroovyStringLiteral(),
    "TARGET_PATH" to downloadTarget.toCanonicalPath().toGroovyStringLiteral(),
    "GRADLE_TASK_NAME" to taskName.toGroovyStringLiteral(),
    "GRADLE_PROJECT_PATH" to externalProjectPath.toGroovyStringLiteral(),
  ))
}

fun loadTaskInitScript(
  projectPath: String,
  taskName: String,
  taskType: String,
  toolingExtensionClasses: Set<Class<*>>,
  taskConfiguration: String?
): String {
  return joinInitScripts(
    loadToolingExtensionProvidingInitScript(toolingExtensionClasses),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/TaskInit.gradle", mapOf(
      "PROJECT_PATH" to projectPath.toGroovyStringLiteral(),
      "TASK_NAME" to taskName.toGroovyStringLiteral(),
      "TASK_TYPE" to taskType,
      "TASK_CONFIGURATION" to (taskConfiguration ?: "")
    ))
  )
}

fun createTargetPathMapperInitScript(): Path {
  val initScript = loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/MapperInit.gradle")
  return createInitScript(MAPPER_INIT_SCRIPT_NAME, initScript)
}

@ApiStatus.Internal
fun attachTargetPathMapperInitScript(settings: GradleExecutionSettings) {
  val file = createTargetPathMapperInitScript()
  settings.prependArguments(GradleConstants.INIT_SCRIPT_CMD_OPTION, file.toString())
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

fun loadCommonTasksUtilsScript():String {
  return loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/GradleTasksUtil.gradle")
}

fun loadCommonDebuggerUtilsScript():String {
  return loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/GradleDebuggerUtil.gradle")
}

fun loadJvmDebugInitScript(): String {
  return loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/JvmDebugInit.gradle")
}

fun loadJvmOptionsInitScript(
  tasks: List<String>,
  jvmArgs: List<String>,
): String {
  return loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/JvmOptionsInit.gradle", mapOf(
    "TASKS" to tasks.toGroovyListLiteral { toGroovyStringLiteral() },
    "JVM_ARGS" to jvmArgs.toGroovyListLiteral { toGroovyStringLiteral() }
  ))
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

fun loadApplicationInitScript(
  gradlePath: String,
  runAppTaskName: String,
  mainClassToRun: String,
  javaExePath: String,
  sourceSetName: String,
  params: String?,
  intelliJRtPath: String?,
  workingDirectory: String?,
  useManifestJar: Boolean,
  useArgsFile: Boolean,
  useClasspathFile: Boolean
): String {
  return joinInitScripts(
    loadToolingExtensionProvidingInitScript(
      GradleToolingExtensionImplClass::class.java,
      GradleToolingExtensionClass::class.java
    ),
    loadInitScript(
      "/org/jetbrains/plugins/gradle/tooling/internal/init/ApplicationTaskInitScript.gradle",
      mapOf(
        "GRADLE_PATH" to gradlePath.toGroovyStringLiteral(),
        "RUN_APP_TASK_NAME" to runAppTaskName.toGroovyStringLiteral(),
        "MAIN_CLASS_TO_RUN" to mainClassToRun.toGroovyStringLiteral(),
        "JAVA_EXE_PATH" to "mapPath(${javaExePath.toGroovyStringLiteral()})",
        "SOURCE_SET_NAME" to sourceSetName.toGroovyStringLiteral(),
        "INTELLIJ_RT_PATH" to if (intelliJRtPath.isNullOrEmpty()) "null" else "mapPath(${intelliJRtPath.toGroovyStringLiteral()})",
        "WORKING_DIRECTORY" to if (workingDirectory.isNullOrEmpty()) "null" else "mapPath(${workingDirectory.toGroovyStringLiteral()})",
        // params should be kept as is; they will be embedded into the init-script directly
        "PARAMS" to if (params.isNullOrEmpty()) "// NO PARAMS" else params,
        "USE_MANIFEST_JAR" to useManifestJar.toString(),
        "USE_ARGS_FILE" to useArgsFile.toString(),
        "USE_CLASSPATH_FILE" to useClasspathFile.toString()
      )
    )
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

@ApiStatus.Experimental
fun joinInitScripts(vararg initScripts: String): String {
  return joinInitScripts(initScripts.asList())
}

@ApiStatus.Experimental
fun joinInitScripts(initScripts: List<String>): String {
  return initScripts.joinToString(System.lineSeparator())
}

private fun loadInitScript(resourcePath: String, parameters: Map<String, String> = emptyMap()): String {
  return loadInitScript(Init::class.java, resourcePath, parameters)
}

@ApiStatus.Experimental
fun loadInitScript(aClass: Class<*>, resourcePath: String, parameters: Map<String, String> = emptyMap()): String {
  val resource = aClass.getResource(resourcePath)
  if (resource == null) {
    throw IllegalArgumentException("Cannot find init file $resourcePath")
  }
  var script = try {
    resource.readText()
  }
  catch (e: IOException) {
    throw IllegalStateException("Cannot read init file $resourcePath", e)
  }
  for ((key, value) in parameters) {
    val replacement = Matcher.quoteReplacement(value)
    script = script.replaceFirst(key.toRegex(), replacement)
  }
  return script
}

fun createInitScript(prefix: String, content: String): Path {
  val sanitizedPrefix = FileUtil.sanitizeFileName(prefix)
  val contentBytes = content.encodeToByteArray()
  val tempDirectory = Path.of(FileUtil.getTempDirectory())
  tempDirectory.createDirectories()
  var suffix = 0
  while (true) {
    suffix++
    val candidateName = sanitizedPrefix + suffix + "." + GradleConstants.EXTENSION
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

fun loadToolingExtensionProvidingInitScript(vararg toolingExtensionClasses: Class<*>): String {
  return loadToolingExtensionProvidingInitScript(toolingExtensionClasses.toSet())
}

fun loadToolingExtensionProvidingInitScript(toolingExtensionClasses: Set<Class<*>>): String {
  val tapiClasspath = getToolingExtensionsJarPaths(toolingExtensionClasses)
    .toGroovyListLiteral { "mapPath(" + toGroovyStringLiteral() + ")" }
  return loadInitScript(
    "/org/jetbrains/plugins/gradle/tooling/internal/init/ClassPathExtensionInitScript.gradle",
    mapOf("EXTENSIONS_JARS_PATH" to tapiClasspath)
  )
}

private fun isContentEquals(path: Path, content: ByteArray): Boolean {
  return content.size.toLong() == path.fileSize() &&
         content.contentEquals(path.readBytes())
}

private fun getToolingExtensionsJarPaths(toolingExtensionClasses: Set<Class<*>>): Set<String> {
  return ContainerUtil.map2SetNotNull(toolingExtensionClasses) { aClass: Class<*> ->
    val path = PathManager.getJarPathForClass(aClass) ?: return@map2SetNotNull null
    if (FileUtilRt.getNameWithoutExtension(path) == "gradle-api-" + GradleVersion.current().baseVersion) {
      LOG.warn("The gradle api jar shouldn't be added to the gradle daemon classpath: {$aClass,$path}")
      return@map2SetNotNull null
    }
    if (isExcluded(path)) {
      val message = "Attempting to pass an excluded IDEA component path [$path] into Gradle Daemon for class [$aClass]"
      if (ApplicationManagerEx.isInIntegrationTest()) {
        throw IllegalArgumentException(message)
      }
      else {
        LOG.warn(message)
      }
    }
    return@map2SetNotNull FileUtil.toCanonicalPath(path)
  }
}

private fun isExcluded(jarPath: String): Boolean {
  val normalizedJarPath = FileUtil.normalize(jarPath)
  return EXCLUDED_JAR_SUFFIXES.any { suffix -> normalizedJarPath.endsWith(suffix) }
}
