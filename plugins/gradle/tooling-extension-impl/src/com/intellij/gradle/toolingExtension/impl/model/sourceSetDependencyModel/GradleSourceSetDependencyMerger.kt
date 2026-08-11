// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetDependencyModel

import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.plugins.gradle.model.AbstractExternalDependency
import org.jetbrains.plugins.gradle.model.ExternalDependency
import org.jetbrains.plugins.gradle.model.DefaultFileCollectionDependency
import org.jetbrains.plugins.gradle.model.ExternalLibraryDependency
import org.jetbrains.plugins.gradle.model.ExternalMultiLibraryDependency
import org.jetbrains.plugins.gradle.model.ExternalProjectDependency
import org.jetbrains.plugins.gradle.model.FileCollectionDependency
import java.nio.file.Path

@Internal
object GradleSourceSetDependencyMerger {

  const val COMPILE_SCOPE: String = "COMPILE"
  const val RUNTIME_SCOPE: String = "RUNTIME"
  const val PROVIDED_SCOPE: String = "PROVIDED"

  /**
   * Replaces each [FileCollectionDependency] from [classpathDependencies]
   * whose files are found in [configurationDependencies]
   * with the matching [ExternalDependency] instances.
   *
   * File dependencies with no files in [configurationDependencies] are kept as-is —
   * this preserves all flags (e.g. `excludedFromIndexing`).
   */
  @JvmStatic
  fun enrichDependencies(
    classpathDependencies: Collection<ExternalDependency>,
    configurationDependencies: Collection<ExternalDependency>,
  ): Collection<ExternalDependency> {
    val configurationDependencyIndex = HashMap<Path, ExternalDependency>()
    for (dependency in configurationDependencies) {
      for (path in dependency.paths()) {
        configurationDependencyIndex[path] = dependency
      }
    }

    val result = LinkedHashSet<ExternalDependency>()
    for (classpathDependency in classpathDependencies) {
      when (classpathDependency) {
        is FileCollectionDependency ->
          enrichFileDependency(classpathDependency, configurationDependencyIndex, result)
        else -> result.add(classpathDependency)
      }
    }
    return result
  }

  private fun enrichFileDependency(
    classpathDependency: FileCollectionDependency,
    configurationDependencyIndex: HashMap<Path, ExternalDependency>,
    resultDependencies: MutableCollection<ExternalDependency>,
  ) {
    val paths = classpathDependency.paths()

    val unmatchedPaths = LinkedHashSet<Path>()
    for (path in paths) {
      val enrichedDependency = configurationDependencyIndex[path]
      if (enrichedDependency != null) {
        resultDependencies.add(enrichedDependency)
      }
      else {
        unmatchedPaths.add(path)
      }
    }
    if (unmatchedPaths.size == paths.size) {
      resultDependencies.add(classpathDependency)
    }
    else if (unmatchedPaths.isNotEmpty()) {
      val unmatchedFiles = unmatchedPaths.map(Path::toFile)
      resultDependencies.add(DefaultFileCollectionDependency(classpathDependency).also {
        it.files = unmatchedFiles
      })
    }
  }

  @JvmStatic
  fun mergeDependencies(
    compileDependencies: Collection<ExternalDependency>,
    runtimeDependencies: Collection<ExternalDependency>,
    providedDependencies: Collection<ExternalDependency>,
  ): Collection<ExternalDependency> {
    val compilePaths = compileDependencies.mapTo(HashSet()) { it.paths() }
    val runtimePaths = runtimeDependencies.mapTo(HashSet()) { it.paths() }
    val providedPaths = providedDependencies.mapTo(HashSet()) { it.paths() }

    val result = ArrayList<ExternalDependency>()

    for (dependency in compileDependencies) {
      val paths = dependency.paths()
      dependency as AbstractExternalDependency
      dependency.scope = when (paths) {
        in providedPaths -> PROVIDED_SCOPE
        in runtimePaths -> COMPILE_SCOPE
        else -> PROVIDED_SCOPE
      }
      result.add(dependency)
    }

    for (dependency in runtimeDependencies) {
      val paths = dependency.paths()
      if (paths in compilePaths) continue
      dependency as AbstractExternalDependency
      dependency.scope = when (paths) {
        in providedPaths -> PROVIDED_SCOPE
        else -> RUNTIME_SCOPE
      }
      result.add(dependency)
    }

    for (dependency in providedDependencies) {
      val paths = dependency.paths()
      if (paths in compilePaths) continue
      if (paths in runtimePaths) continue
      dependency as AbstractExternalDependency
      dependency.scope = PROVIDED_SCOPE
      result.add(dependency)
    }

    return result
  }

  @Suppress("IO_FILE_USAGE")
  private fun ExternalDependency.paths(): Collection<Path> =
    files().map(java.io.File::toPath)

  @Suppress("IO_FILE_USAGE")
  private fun ExternalDependency.files(): Collection<java.io.File> =
    when (this) {
      is FileCollectionDependency -> getFiles()
      is ExternalLibraryDependency -> setOf(getFile())
      is ExternalMultiLibraryDependency -> getFiles()
      is ExternalProjectDependency -> getProjectDependencyArtifacts()
      else -> emptySet()
    }
}
