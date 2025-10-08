// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.inputStream
import kotlin.io.path.readLines

@ApiStatus.Internal
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

  private fun getArchivedCompiledClassesLocationIfIsRunningFromBazelOut(): String? {
    val utilJar = PathManager.getJarPathForClass(PathManager::class.java)
    val bazelOutPattern = Paths.get("bazel-out", "jvm-fastbuild").toString()
    val index = utilJar?.indexOf(bazelOutPattern) ?: -1
    val isRunningFromBazelOut = index != -1 && utilJar!!.endsWith(".jar")
    return if (isRunningFromBazelOut) utilJar.take(index + bazelOutPattern.length) else null
  }

  @JvmStatic
  val archivedCompiledClassesMapping: Map<String, String>? by lazy(LazyThreadSafetyMode.PUBLICATION) {
    /**
     * Returns a map of IntelliJ modules to .jar absolute paths, e.g.:
     * "production/intellij.platform.util" => ".../production/intellij.platform.util/$hash.jar"
     */
    computeArchivedCompiledClassesMapping()
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
      lines = Paths.get(filePath).readLines()
    }
    catch (_: Exception) {
      log("Failed to load jars mappings from $filePath")
      return null
    }
    val mapping: MutableMap<String, String> = mutableMapOf()
    for (line in lines) {
      val split = line.split("=", limit = 2)
      if (split.size < 2) {
        log("Ignored jars mapping line: $line")
        continue
      }
      mapping[split[0]] = split[1]
    }
    return mapping
  }

  private fun computeArchivedCompiledClassesMappingIfIsRunningFromBazelOut(): Map<String, String>? {
    val targetsFile: BazelTargetsInfo.TargetsFile
    try {
      targetsFile = BazelTargetsInfo.loadTargetsFileFromBazelTargetsJson(PathManager.getHomeDir())
    }
    catch (_: Exception) {
      log("Failed to load targets info from bazel-targets.json")
      return null
    }

    val mapping: MutableMap<String, String> = mutableMapOf()
    targetsFile.modules.forEach { (moduleName, targetsFileModuleDescription) ->
      if (targetsFileModuleDescription.productionJars.isNotEmpty()) {
        mapping["production/$moduleName"] = targetsFileModuleDescription.productionJars.map { PathManager.getHomeDir().resolve(it).toString() }.single()
      }
      if (targetsFileModuleDescription.testJars.isNotEmpty()) {
        mapping["test/$moduleName"] = targetsFileModuleDescription.testJars.map { PathManager.getHomeDir().resolve(it).toString() }.single()
      }
    }
    return mapping
  }

  private fun log(x: String) {
    System.err.println(x)
  }

  private object BazelTargetsInfo {
    @OptIn(ExperimentalSerializationApi::class)
    fun loadTargetsFileFromBazelTargetsJson(projectRoot: Path): TargetsFile {
      val bazelTargetsJsonFile = projectRoot.resolve("build").resolve("bazel-targets.json")
      return bazelTargetsJsonFile.inputStream().use { Json { ignoreUnknownKeys = true }.decodeFromStream<TargetsFile>(it) }
    }

    @Serializable
    data class TargetsFileModuleDescription(
      val productionJars: List<String>,
      val testJars: List<String>,
    )

    @Serializable
    data class TargetsFile(
      val modules: Map<String, TargetsFileModuleDescription>,
    )
  }
}
