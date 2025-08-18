// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleInitScriptUtil")

package org.jetbrains.plugins.gradle.service.execution

import com.amazon.ion.IonType
import com.google.common.collect.Multimap
import com.google.gson.GsonBuilder
import com.intellij.gradle.toolingExtension.GradleToolingExtensionClass
import com.intellij.gradle.toolingExtension.impl.GradleToolingExtensionImplClass
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.externalSystem.rt.ExternalSystemRtClass
import gnu.trove.TObjectHash
import groovy.lang.MissingMethodException
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.ImplicitContextKeyed
import org.apache.commons.lang3.StringUtils
import org.gradle.api.invocation.Gradle
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.task.LazyVersionSpecificInitScript
import org.jetbrains.plugins.gradle.service.task.VersionSpecificInitScript
import org.jetbrains.plugins.gradle.tooling.internal.init.Init
import org.jetbrains.plugins.gradle.tooling.proxy.Main
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.slf4j.LoggerFactory
import org.slf4j.jul.JDK14LoggerFactory
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

@ApiStatus.Internal
const val MAIN_INIT_SCRIPT_NAME: String = "ijInit"

@ApiStatus.Internal
const val MAPPER_INIT_SCRIPT_NAME: String = "ijMapper"

@ApiStatus.Internal
const val WRAPPER_INIT_SCRIPT_NAME: String = "ijWrapper"

@ApiStatus.Internal
const val TEST_INIT_SCRIPT_NAME: String = "ijTestInit"

@ApiStatus.Internal
const val IDEA_PLUGIN_CONFIGURATOR_SCRIPT_NAME: String = "ijIdeaPluginConfigurator"

@ApiStatus.Internal
const val ARTIFACT_DOWNLOADER_SCRIPT_NAME: String = "ijArtifactDownloader"

@ApiStatus.Internal
const val HOTSWAP_DETECTION_SCRIPT_NAME: String = "ijHotswapDetection"

@JvmField
val GRADLE_TOOLING_EXTENSION_CLASSES: Set<Class<*>> = setOf(
  SystemInfoRt::class.java, // intellij.platform.util.rt
  ExternalSystemRtClass::class.java, // intellij.platform.externalSystem.rt
  GradleToolingExtensionClass::class.java, // intellij.gradle.toolingExtension
  GradleToolingExtensionImplClass::class.java, // intellij.gradle.toolingExtension.impl

  // the set of dependencies required for the modules above
  Unit::class.java, // kotlin-stdlib
  GsonBuilder::class.java, // gson
  IonType::class.java,  // ion serialisation
  Multimap::class.java, // guava
  StringUtils::class.java, // apache commons
  TObjectHash::class.java, // trove hashing
  Span::class.java, // opentelemetry
  ImplicitContextKeyed::class.java // opentelemetry-context
)

@JvmField
@ApiStatus.Internal
val GRADLE_TOOLING_EXTENSION_PROXY_CLASSES: Set<Class<*>> = GRADLE_TOOLING_EXTENSION_CLASSES + setOf(
  Gradle::class.java, // gradle-api jar
  LoggerFactory::class.java, JDK14LoggerFactory::class.java, // logging jars
  Main::class.java, // gradle tooling proxy module
  MissingMethodException::class.java // groovy runtime for serialization
)

@ApiStatus.Internal
fun createMainInitScript(isBuildSrcProject: Boolean, toolingExtensionClasses: Set<Class<*>>): Path {
  val initScript = joinInitScripts(
    loadToolingExtensionProvidingInitScript(GRADLE_TOOLING_EXTENSION_CLASSES + toolingExtensionClasses),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/RegistryProcessor.gradle"),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/JetGradlePlugin.gradle"),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/Init.gradle", mapOf(
      "IS_BUILD_SCR_PROJECT" to isBuildSrcProject.toString()
    ))
  )
  return createInitScript(MAIN_INIT_SCRIPT_NAME, initScript)
}

@ApiStatus.Internal
fun createIdeaPluginConfiguratorInitScript(): Path {
  val initScript = joinInitScripts(
    loadToolingExtensionProvidingInitScript(GRADLE_TOOLING_EXTENSION_CLASSES),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/IdeaPluginConfigurator.gradle")
  )
  return createInitScript(IDEA_PLUGIN_CONFIGURATOR_SCRIPT_NAME, initScript)
}

@ApiStatus.Internal
fun loadDownloadArtifactInitScript(
  dependencyNotation: String,
  taskName: String,
  downloadTarget: EelPath,
  projectPath: EelPath,
): List<VersionSpecificInitScript> {
  return listOf(
    LazyVersionSpecificInitScript(
      filePrefix = ARTIFACT_DOWNLOADER_SCRIPT_NAME,
      isApplicable = { GradleVersionUtil.isGradleAtLeast(it, "5.6") },
      scriptSupplier = {
        loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/downloadArtifact.gradle", mapOf(
          "DEPENDENCY_NOTATION" to dependencyNotation.toGroovyStringLiteral(),
          "TARGET_PATH" to downloadTarget.toString().toGroovyStringLiteral(),
          "GRADLE_TASK_NAME" to taskName.toGroovyStringLiteral(),
          "GRADLE_PROJECT_PATH" to projectPath.toString().toGroovyStringLiteral(),
        ))
      }
    ),
    LazyVersionSpecificInitScript(
      filePrefix = ARTIFACT_DOWNLOADER_SCRIPT_NAME,
      isApplicable = { GradleVersionUtil.isGradleOlderThan(it, "5.6") },
      scriptSupplier = {
        loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/legacyDownloadArtifact.gradle", mapOf(
          "DEPENDENCY_NOTATION" to dependencyNotation.toGroovyStringLiteral(),
          "TARGET_PATH" to downloadTarget.toString().toGroovyStringLiteral(),
          "GRADLE_TASK_NAME" to taskName.toGroovyStringLiteral(),
          "GRADLE_PROJECT_PATH" to projectPath.toString().toGroovyStringLiteral(),
        ))
      }
    )
  )
}

@ApiStatus.Internal
fun loadCollectDependencyInitScript(
  taskName: String,
  outputFile: EelPath,
): String {
  return joinInitScripts(
    loadToolingExtensionProvidingInitScript(GRADLE_TOOLING_EXTENSION_CLASSES + setOf(
      GsonBuilder::class.java // required by GradleDependencyReportGenerator
    )),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/dependencyAnalyser/DependencyAnalyserInit.gradle", mapOf(
      "TASK_NAME" to taskName.toGroovyStringLiteral(),
      "OUTPUT_PATH" to outputFile.toString().toGroovyStringLiteral(),
    ))
  )
}

@ApiStatus.Internal
fun loadTaskInitScript(
  projectPath: String,
  taskName: String,
  taskType: String,
  toolingExtensionClasses: Set<Class<*>>,
  taskConfiguration: String?,
): String {
  return joinInitScripts(
    loadToolingExtensionProvidingInitScript(GRADLE_TOOLING_EXTENSION_CLASSES + toolingExtensionClasses),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/TaskInit.gradle", mapOf(
      "PROJECT_PATH" to projectPath.toGroovyStringLiteral(),
      "TASK_NAME" to taskName.toGroovyStringLiteral(),
      "TASK_TYPE" to taskType,
      "TASK_CONFIGURATION" to (taskConfiguration ?: "")
    ))
  )
}

@ApiStatus.Internal
fun createTargetPathMapperInitScript(): Path {
  val initScript = loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/MapperInit.gradle")
  return createInitScript(MAPPER_INIT_SCRIPT_NAME, initScript)
}

@ApiStatus.Internal
fun createWrapperInitScript(
  gradleVersion: GradleVersion?,
  jarFile: File,
  scriptFile: File,
  fileWithPathToProperties: File,
): Path {
  val initScript = loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/WrapperInit.gradle", mapOf(
    "GRADLE_VERSION" to (gradleVersion?.version?.toGroovyStringLiteral() ?: "null"),
    "JAR_FILE" to jarFile.path.toGroovyStringLiteral(),
    "SCRIPT_FILE" to scriptFile.path.toGroovyStringLiteral(),
    "FILE_WITH_PATH_TO_PROPERTIES" to fileWithPathToProperties.path.toGroovyStringLiteral()
  ))
  return createInitScript(WRAPPER_INIT_SCRIPT_NAME, initScript)
}

@ApiStatus.Internal
fun createTestInitScript(): Path {
  val initScript = joinInitScripts(
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/TestInit.gradle")
  )
  return createInitScript(TEST_INIT_SCRIPT_NAME, initScript)
}

@ApiStatus.Internal
fun loadJvmDebugInitScript(): String {
  return joinInitScripts(
    loadToolingExtensionProvidingInitScript(GRADLE_TOOLING_EXTENSION_CLASSES),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/JvmDebugInit.gradle")
  )
}

@ApiStatus.Internal
fun loadJvmOptionsInitScript(
  tasks: List<String>,
  jvmArgs: List<String>,
): String {
  return loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/JvmOptionsInit.gradle", mapOf(
    "TASKS" to tasks.toGroovyListLiteral { toGroovyStringLiteral() },
    "JVM_ARGS" to jvmArgs.toGroovyListLiteral { toGroovyStringLiteral() }
  ))
}

@ApiStatus.Internal
fun loadHotswapDetectionInitScript(
  isImprovedHotswapDetectionEnabled: Boolean,
  outputFile: EelPath,
): List<VersionSpecificInitScript> {
  return listOf(
    LazyVersionSpecificInitScript(
      filePrefix = HOTSWAP_DETECTION_SCRIPT_NAME,
      isApplicable = { isImprovedHotswapDetectionEnabled && GradleVersionUtil.isGradleOlderThan(it, "6.8") },
      scriptSupplier = {
        joinInitScripts(
          loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/projectTaskRunner/ImprovedHotswapDetectionUtils.gradle"),
          loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/projectTaskRunner/ImprovedHotswapDetectionInit.gradle", mapOf(
            "OUTPUT_PATH" to outputFile.toString().toGroovyStringLiteral(),
          ))
        )
      }
    ),
    LazyVersionSpecificInitScript(
      filePrefix = HOTSWAP_DETECTION_SCRIPT_NAME,
      isApplicable = { isImprovedHotswapDetectionEnabled && GradleVersionUtil.isGradleAtLeast(it, "6.8") },
      scriptSupplier = {
        joinInitScripts(
          loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/projectTaskRunner/ImprovedHotswapDetectionUtils.gradle"),
          loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/projectTaskRunner/ImprovedHotswapDetectionUsingServiceInit.gradle", mapOf(
            "OUTPUT_PATH" to outputFile.toString().toGroovyStringLiteral(),
          ))
        )
      }
    ),
    LazyVersionSpecificInitScript(
      filePrefix = HOTSWAP_DETECTION_SCRIPT_NAME,
      isApplicable = { !isImprovedHotswapDetectionEnabled && GradleVersionUtil.isGradleOlderThan(it, "6.8") },
      scriptSupplier = {
        loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/projectTaskRunner/HotswapDetectionInit.gradle", mapOf(
          "OUTPUT_PATH" to outputFile.toString().toGroovyStringLiteral(),
        ))
      }
    ),
    LazyVersionSpecificInitScript(
      filePrefix = HOTSWAP_DETECTION_SCRIPT_NAME,
      isApplicable = { !isImprovedHotswapDetectionEnabled && GradleVersionUtil.isGradleAtLeast(it, "6.8") },
      scriptSupplier = {
        loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/projectTaskRunner/HotswapDetectionUsingServiceInit.gradle", mapOf(
          "OUTPUT_PATH" to outputFile.toString().toGroovyStringLiteral(),
        ))
      }
    )
  )
}

private val JUNIT_3_COMPARISON_FAILURE = listOf("junit.framework.ComparisonFailure")
private val JUNIT_4_COMPARISON_FAILURE = listOf("org.junit.ComparisonFailure")
private val ASSERTION_FAILED_ERROR = listOf("org.opentest4j.AssertionFailedError")
private val FILE_COMPARISON_FAILURE = listOf("com.intellij.rt.execution.junit.FileComparisonFailure",
                                             "junit.framework.ComparisonFailure")

@ApiStatus.Internal
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

@ApiStatus.Internal
fun loadFileComparisonTestLoggerInitScript(): String {
  return joinInitScripts(
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/TestEventLogger.gradle"),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/FileComparisonTestEventLogger.gradle"),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/FileComparisonTestEventLoggerInit.gradle"),
    loadEnhanceGradleDaemonClasspathInit(listOf(FILE_COMPARISON_FAILURE))
  )
}

@ApiStatus.Internal
fun loadApplicationInitScript(
  gradlePath: String,
  runAppTaskName: String,
  mainClassToRun: String,
  javaExePath: String,
  sourceSetName: String,
  params: String?,
  definitions: String?,
  intelliJRtPath: String?,
  workingDirectory: String?,
  useManifestJar: Boolean,
  useArgsFile: Boolean,
  useClasspathFile: Boolean,
  javaModuleName: String?,
): String {
  return joinInitScripts(
    loadToolingExtensionProvidingInitScript(GRADLE_TOOLING_EXTENSION_CLASSES),
    loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/ApplicationTaskInitScript.gradle", mapOf(
      "GRADLE_PATH" to gradlePath.toGroovyStringLiteral(),
      "RUN_APP_TASK_NAME" to runAppTaskName.toGroovyStringLiteral(),
      "MAIN_CLASS_TO_RUN" to mainClassToRun.toGroovyStringLiteral(),
      "JAVA_EXE_PATH" to "mapPath(${javaExePath.toGroovyStringLiteral()})",
      "SOURCE_SET_NAME" to sourceSetName.toGroovyStringLiteral(),
      "INTELLIJ_RT_PATH" to if (intelliJRtPath.isNullOrEmpty()) "null" else "mapPath(${intelliJRtPath.toGroovyStringLiteral()})",
      "WORKING_DIRECTORY" to if (workingDirectory.isNullOrEmpty()) "null" else "mapPath(${workingDirectory.toGroovyStringLiteral()})",
      // params should be kept as is; they will be embedded into the init-script directly
      "PARAMS" to if (params.isNullOrEmpty()) "// NO PARAMS" else params,
      "DEFS" to if (definitions.isNullOrEmpty()) "// NO DEFS" else definitions,
      "USE_MANIFEST_JAR" to useManifestJar.toString(),
      "USE_ARGS_FILE" to useArgsFile.toString(),
      "USE_CLASSPATH_FILE" to useClasspathFile.toString(),
      "JAVA_MODULE_NAME" to if (javaModuleName.isNullOrEmpty()) "null" else javaModuleName.toGroovyStringLiteral()
    ))
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

fun joinInitScripts(vararg initScripts: String): String {
  return joinInitScripts(initScripts.asList())
}

fun joinInitScripts(initScripts: Iterable<String>): String {
  return initScripts.joinToString(System.lineSeparator())
}

private fun loadInitScript(resourcePath: String, parameters: Map<String, String> = emptyMap()): String {
  return loadInitScript(Init::class.java, resourcePath, parameters)
}

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

fun loadToolingExtensionProvidingInitScript(toolingExtensionClasses: Set<Class<*>>): String {
  val tapiClasspath = getToolingExtensionsJarPaths(toolingExtensionClasses)
  return loadInitScript("/org/jetbrains/plugins/gradle/tooling/internal/init/ClassPathExtensionInitScript.gradle", mapOf(
    "EXTENSIONS_JARS_PATH" to tapiClasspath.toGroovyListLiteral { "mapPath(" + toGroovyStringLiteral() + ")" }
  ))
}

private fun isContentEquals(path: Path, content: ByteArray): Boolean {
  return content.size.toLong() == path.fileSize() &&
         content.contentEquals(path.readBytes())
}

@ApiStatus.Internal
fun getToolingExtensionsJarPaths(toolingExtensionClasses: Iterable<Class<*>>): Set<String> {
  return toolingExtensionClasses.mapNotNullTo(LinkedHashSet(), ::getToolingExtensionsJarPath)
}

private fun getToolingExtensionsJarPath(toolingExtensionClass: Class<*>): String? {
  val path = PathManager.getJarPathForClass(toolingExtensionClass) ?: return null
  if (FileUtilRt.getNameWithoutExtension(path) == "gradle-api-" + GradleVersion.current().baseVersion) {
    LOG.warn("The gradle api jar shouldn't be added to the gradle daemon classpath: {$toolingExtensionClass,$path}")
    return null
  }
  if (isExcluded(path)) {
    val message = "Attempting to pass an excluded IDEA component path [$path] into Gradle Daemon for class [$toolingExtensionClass]"
    if (ApplicationManagerEx.isInIntegrationTest()) {
      throw IllegalArgumentException(message)
    }
    else {
      LOG.warn(message)
    }
  }
  return FileUtil.toCanonicalPath(path)
}

private fun isExcluded(jarPath: String): Boolean {
  val normalizedJarPath = FileUtil.normalize(jarPath)
  return EXCLUDED_JAR_SUFFIXES.any { suffix -> normalizedJarPath.endsWith(suffix) }
}
