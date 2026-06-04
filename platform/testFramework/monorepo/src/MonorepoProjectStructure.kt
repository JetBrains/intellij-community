// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.testFramework.monorepo

import com.intellij.openapi.application.ArchivedCompilationContextUtil
import com.intellij.openapi.application.PathManager
import com.intellij.project.loadIntelliJProject
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import java.nio.file.FileSystems
import java.nio.file.Path

object MonorepoProjectStructure {
  val communityHomePath: String = PlatformTestUtil.getCommunityPath()
  val communityRoot: Path = Path.of(communityHomePath)
  val communityProject: JpsProject by lazy { loadIntelliJProject(communityRoot) }

  val JpsModule.baseDirectory: Path
    get() = JpsModelSerializationDataService.getModuleExtension(this)!!.baseDirectoryPath

  fun JpsModule.isFromCommunity(): Boolean = baseDirectory.startsWith(communityRoot)

  fun JpsLibrary.isFromCommunity(): Boolean = getPaths(JpsOrderRootType.COMPILED).all { it.startsWith(communityRoot) }
}

fun JpsModule.hasProductionSources(): Boolean = getSourceRoots(JavaSourceRootType.SOURCE).iterator().hasNext()

/**
 * Calls [processor] for the path containing the production output of [this@processModuleProductionOutput].
 * Works both when module output is located in a directory and when it's packed in a JAR.
 */
fun <T> JpsModule.processProductionOutput(processor: (outputRoot: Path) -> T): T {
  val archivedCompiledClassesMapping = getArchivedCompiledClassesMapping(project)
  val outputJarPath = archivedCompiledClassesMapping?.get("production/$name")
  if (outputJarPath == null) {
    val outputDirectoryPath = JpsJavaExtensionService.getInstance().getOutputDirectoryPath(this, false)
                              ?: error("Output directory is not specified for '$name'")
    return processor(outputDirectoryPath)
  }
  else {
    return FileSystems.newFileSystem(Path.of(outputJarPath)).use {
      processor(it.rootDirectories.single())
    }
  }
}

val JpsModule.productionOutputPaths: List<Path>
  get() {
    val archivedCompiledClassesMapping = getArchivedCompiledClassesMapping(project)
    if (archivedCompiledClassesMapping != null) {
      val outputJarPath = archivedCompiledClassesMapping["production/$name"]
      return outputJarPath?.let { listOf(Path.of(it)) } ?: emptyList()
    }
    return listOf(JpsJavaExtensionService.getInstance().getOutputDirectoryPath(this, false) ?: error("Output directory is not specified for '$name'"))
  }

val JpsModule.testOutputPaths: List<Path>
  get() {
    val archivedCompiledClassesMapping = getArchivedCompiledClassesMapping(project)
    if (archivedCompiledClassesMapping != null) {
      val outputJarPath = archivedCompiledClassesMapping["test/$name"]
      return outputJarPath?.let { listOf(Path.of(it)) } ?: emptyList()
    }
    return listOf(JpsJavaExtensionService.getInstance().getOutputDirectoryPath(this, true) ?: error("Test output directory is not specified for '$name'"))
  }

private fun getArchivedCompiledClassesMapping(project: JpsProject): Map<String, String>? {
  val compiledClassesMapping = ArchivedCompilationContextUtil.archivedCompiledClassesMapping
  if (compiledClassesMapping != null) {
    val projectHome = JpsModelSerializationDataService.getBaseDirectoryPath(project)
    val currentProcessHome = PathManager.getHomeDir()
    require(currentProcessHome == projectHome || currentProcessHome.resolve("community") == projectHome) {
      "Output classes mapping is requested for the project at $projectHome, but current process home is $currentProcessHome"
    }
  }
  return compiledClassesMapping
}
