// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.application.ArchivedCompilationContextUtil.getBazelTargetsJsonPath
import com.intellij.platform.bazel.runfiles.BazelRunfiles
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.relativeTo

@Internal
object ArchivedCompilationContextUtil {
  @JvmStatic
  val archivedCompiledClassesLocation: String? by lazy(LazyThreadSafetyMode.PUBLICATION) {
    /**
     * NB: actual jars might be in subdirectories
     */
    var relevantJarsRoot = System.getProperty("intellij.test.jars.location")
    if (relevantJarsRoot != null) {
      return@lazy relevantJarsRoot
    }

    relevantJarsRoot = getArchivedCompiledClassesLocationIfIsRunningFromBazelOut()
    val isRunningFromBazelOut = relevantJarsRoot != null
    if (isRunningFromBazelOut) {
      return@lazy relevantJarsRoot
    }

    return@lazy null
  }

  @Internal
  class LibraryInfo internal constructor(
    val target: String,
    val jars: List<String>,
    val jarTargets: List<String>,
    val sourceJars: List<String>,
  )

  @JvmStatic
  fun findLibraryInfo(libraryName: String, moduleLibraryModuleName: String?): LibraryInfo? {
    val targetsFile = archiveCompilationContextTargetsFile
    val description = if (moduleLibraryModuleName == null) {
      targetsFile.projectLibraries[libraryName]
    }
    else {
      targetsFile.modules[moduleLibraryModuleName]?.moduleLibraries?.get(libraryName)
    }
    return description?.let {
      LibraryInfo(
        target = it.target,
        jars = it.jars,
        jarTargets = it.jarTargets,
        sourceJars = it.sourceJars,
      )
    }
  }

  private val archiveCompilationContextTargetsFile: BazelTargetsInfo.TargetsFile by lazy {
    val projectRoot = PathManager.getHomeDir()
    val targetsJsonPath = getBazelTargetsJsonPath(projectRoot)
    try {
      BazelTargetsInfo.loadTargetsFile(targetsJsonPath)
    }
    catch (e: Exception) {
      error("Failed to load targets info from $targetsJsonPath: " + e.stackTraceToString())
    }
  }

  @JvmStatic
  val archivedCompiledClassesMapping: Map<String, String>? by lazy(LazyThreadSafetyMode.PUBLICATION) {
    /**
     * Returns a map of IntelliJ modules to .jar absolute paths, e.g.:
     * "production/intellij.platform.util" => ".../production/intellij.platform.util/$hash.jar"
     */
    computeArchivedCompiledClassesMapping()
  }

  const val BAZEL_TARGETS_JSON_FILE_PROPERTY: String = "intellij.build.bazel.targets.json.file"

  fun getBazelTargetsJsonPath(projectRoot: Path): Path {
    if (BazelRunfiles.isRunningFromBazel) {
      // relative path, resolve against JAVA_RUNFILES or RUNFILES_MANIFEST_FILE
      val location = System.getProperty(BAZEL_TARGETS_JSON_FILE_PROPERTY)
                     ?: error("Missing property $BAZEL_TARGETS_JSON_FILE_PROPERTY, it's required when running under Bazel")
      log("Running under Bazel, using targets file from Bazel dependencies: rlocationpath=$location")

      val path = BazelRunfiles.resolveRunfilePath(location)
      log("Running under Bazel, using targets file from Bazel dependencies: path=$path")

      return path
    }
    else {
      // absolute path, JAVA_RUNFILES and RUNFILES_MANIFEST_FILE are not set
      var path = System.getProperty(BAZEL_TARGETS_JSON_FILE_PROPERTY)?.let { Paths.get(it) }
      if (path != null) {
        log("Standalone run not under Bazel, using targets file from Bazel dependencies: $path")
        return path
      }

      path = projectRoot.resolve("build").resolve("bazel-targets.json")
      log("Standalone run not under Bazel, using targets file from project: $path")
      return path
    }
  }
}

private fun getArchivedCompiledClassesLocationIfIsRunningFromBazelOut(): String? {
  val utilJar = PathManager.getJarPathForClass(PathManager::class.java)
    ?.let {
      if (BazelRunfiles.isRunningFromBazel) {
        // return the same value for normal case under bazel
        // or in case of //platform/testFramework/monorepo:monorepo-tests_test
        // .../a9dd60d319c66bc49e47c96653e93701/execroot/_main/bazel-out/darwin_arm64-fastbuild/bin/platform/testFramework/monorepo/monorepo-tests_test.runfiles/_main/platform/util/util.jar
        // -> .../a9dd60d319c66bc49e47c96653e93701/execroot/_main/bazel-out/jvm-fastbuild/bin/platform/util/util.jar
        Paths.get(it).toRealPath().absolutePathString()
      } else {
        it
      }
    }
  val bazelOutPattern = Paths.get("bazel-out", "jvm-fastbuild").toString()
  val index = utilJar?.indexOf(bazelOutPattern) ?: -1
  val isRunningFromBazelOut = index != -1 && utilJar!!.endsWith(".jar")
  return if (isRunningFromBazelOut) utilJar.take(index + bazelOutPattern.length) else null
}

private fun computeArchivedCompiledClassesMapping(): Map<String, String>? {
  val filePath = System.getProperty("intellij.test.jars.mapping.file")
  if (filePath.isNullOrBlank()) {
    if (getArchivedCompiledClassesLocationIfIsRunningFromBazelOut() != null) {
      return computeArchivedCompiledClassesMappingIfIsRunningFromBazelOut()
    }
    return null
  }
  val lines: List<String>
  try {
    lines = Files.readAllLines(Paths.get(filePath))
  }
  catch (_: Exception) {
    log("Failed to load jars mappings from $filePath")
    return null
  }
  val mapping = LinkedHashMap<String, String>()
  for (line in lines) {
    val split = line.split('=', limit = 2)
    if (split.size < 2) {
      log("Ignored jars mapping line: $line")
      continue
    }
    mapping[split[0]] = split[1]
  }
  return mapping
}

private fun computeArchivedCompiledClassesMappingIfIsRunningFromBazelOut(): Map<String, String>? {
  val projectRoot = PathManager.getHomeDir()

  val targetsFile = try {
    BazelTargetsInfo.loadTargetsFile(getBazelTargetsJsonPath(projectRoot))
  }
  catch (e: Exception) {
    log("Failed to load targets info from bazel-targets.json: " + e.stackTraceToString())
    return null
  }

  val mapping = LinkedHashMap<String, String>()
  val archivedCompiledClassesLocation = getArchivedCompiledClassesLocationIfIsRunningFromBazelOut()?.let { Paths.get(it).parent.parent }

  for ((moduleName, targetsFileModuleDescription) in targetsFile.modules) {
    if (targetsFileModuleDescription.productionJars.isNotEmpty()) {
      mapping["production/$moduleName"] = targetsFileModuleDescription.productionJars.map { productionJar ->
        if (archivedCompiledClassesLocation != null) {
          val relativeProductionJar = Paths.get(productionJar).relativeTo(Paths.get("out"))
          archivedCompiledClassesLocation.resolve(relativeProductionJar).toString()
        } else {
          projectRoot.resolve(productionJar).toString()
        }
      }.single()
    }
    if (targetsFileModuleDescription.testJars.isNotEmpty()) {
      mapping["test/$moduleName"] = targetsFileModuleDescription.testJars.map { testJar ->
        if (archivedCompiledClassesLocation != null) {
          val relativeTestJar = Paths.get(testJar).relativeTo(Paths.get("out"))
          archivedCompiledClassesLocation.resolve(relativeTestJar).toString()
        } else {
          projectRoot.resolve(testJar).toString()
        }
      }.single()
    }
  }
  return mapping
}

private fun log(x: String) {
  System.err.println(x)
}

private object BazelTargetsInfo {
  private val json = Json { ignoreUnknownKeys = true }

  @OptIn(ExperimentalSerializationApi::class)
  fun loadTargetsFile(file: Path): TargetsFile {
    return Files.newInputStream(file).use { json.decodeFromStream<TargetsFile>(it) }
  }

  @Serializable
  data class LibraryDescription(
    @JvmField val target: String,
    @JvmField val jars: List<String>,
    @JvmField val jarTargets: List<String>,
    @JvmField val sourceJars: List<String>,
  )

  @Serializable
  data class TargetsFileModuleDescription(
    @JvmField val productionJars: List<String>,
    @JvmField val testJars: List<String>,
    @JvmField val moduleLibraries: Map<String, LibraryDescription>,
  )

  @Serializable
  data class TargetsFile(
    @JvmField val modules: Map<String, TargetsFileModuleDescription>,
    @JvmField val projectLibraries: Map<String, LibraryDescription>,
  )
}
