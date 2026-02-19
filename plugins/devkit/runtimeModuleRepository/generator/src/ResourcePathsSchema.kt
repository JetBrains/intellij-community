// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.generator

import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsMavenSettings
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Specifies which resource paths should be used in the runtime module repository by 
 * [RuntimeModuleRepositoryGenerator.generateRuntimeModuleDescriptors].
 * Returned paths are relative to the directory where the runtime module repository file will be generated.
 * For the variant used in development mode, they can start with macros from 
 * [com.intellij.platform.runtime.repository.impl.ResourcePathMacros].
 */
interface ResourcePathsSchema {
  fun moduleOutputPaths(module: JpsModule): List<String>
  fun moduleTestOutputPaths(module: JpsModule): List<String>
  fun libraryPaths(library: JpsLibrary): List<String>
}

/**
 * Specifies resource paths for the runtime module repository generated in the out/classes directory during JPS build.
 */
class JpsCompilationResourcePathsSchema(project: JpsProject) : ResourcePathsSchema {
  private val baseProjectDir = JpsModelSerializationDataService.getBaseDirectoryPath(project)
                               ?: error("Project wasn't loaded from .idea so its base directory cannot be determined")
  private val mavenRepositoryPath = Path(JpsMavenSettings.getMavenRepositoryPath())

  override fun moduleOutputPaths(module: JpsModule): List<String> {
    return listOf("production/${module.name}")
  }

  override fun moduleTestOutputPaths(module: JpsModule): List<String> {
    return listOf("test/${module.name}")
  }

  override fun libraryPaths(library: JpsLibrary): List<String> {
    val files = library.getPaths(JpsOrderRootType.COMPILED)
    return files.map { shortenPathUsingMacros(it) }
  }

  /**
   * Shortens the given path by replacing absolute paths with macros from [com.intellij.platform.runtime.repository.impl.ResourcePathMacros].
   */
  private fun shortenPathUsingMacros(path: Path): String {
    return when {
      path.startsWith(baseProjectDir) -> $$"$PROJECT_DIR$/$${baseProjectDir.relativize(path).invariantSeparatorsPathString}"
      path.startsWith(mavenRepositoryPath) -> $$"$MAVEN_REPOSITORY$/$${mavenRepositoryPath.relativize(path).invariantSeparatorsPathString}"
      else -> path.invariantSeparatorsPathString
    }
  }
}