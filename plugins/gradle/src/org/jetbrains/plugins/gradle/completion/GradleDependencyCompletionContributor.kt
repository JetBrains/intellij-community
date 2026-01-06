// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.completion

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.completion.api.*
import org.jetbrains.plugins.gradle.service.cache.GradleLocalRepositoryIndexer

@ApiStatus.Internal
class GradleDependencyCompletionContributor : DependencyCompletionContributor {

  override fun isApplicable(context: DependencyCompletionContext): Boolean {
    return context is GradleDependencyCompletionContext
  }

  override suspend fun search(request: DependencyCompletionRequest): List<DependencyCompletionResult> {
    val searchString = request.searchString.trim()

    val parts = searchString.split(":")

    val groupSubstring = parts.getOrNull(0).orEmpty()
    val artifactSubstring = parts.getOrNull(1).orEmpty()
    val versionSubstring = parts.getOrNull(2).orEmpty()

    return when (parts.size) {
      1 -> searchSingle(request, groupSubstring)
      else -> searchFull(request, groupSubstring, artifactSubstring, versionSubstring)
    }
  }

  /**
   * Finds dependencies all matching group or artifact substring
   */
  private fun searchSingle(request: DependencyCompletionRequest, substring: String): List<DependencyCompletionResult> {
    val eelDescriptor = request.context.eelDescriptor
    val indexer = service<GradleLocalRepositoryIndexer>()
    val matchesOnGroup = indexer.groups(eelDescriptor)
      .filter { it.contains(substring, ignoreCase = true) }
      .flatMap { group ->
        indexer.artifacts(eelDescriptor, group)
          .flatMap { artifact ->
            indexer.versions(eelDescriptor, group, artifact)
              .map { version -> DependencyCompletionResult(group, artifact, version) }
          }
      }.toSet()
    val matchesOnArtifact = indexer.artifacts(eelDescriptor)
      .filter { it.contains(substring, ignoreCase = true) }
      .flatMap { artifact ->
        indexer.groups(eelDescriptor, artifact)
          .flatMap { group ->
            indexer.versions(eelDescriptor, group, artifact)
              .map { version -> DependencyCompletionResult(group, artifact, version) }
          }
      }.toSet()
    return (matchesOnGroup + matchesOnArtifact).toList()
  }

  private fun searchFull(
    request: DependencyCompletionRequest,
    groupSubstring: String,
    artifactSubstring: String,
    versionSubstring: String,
  ): List<DependencyCompletionResult> {
    val eelDescriptor = request.context.eelDescriptor
    val indexer = service<GradleLocalRepositoryIndexer>()
    return indexer.groups(eelDescriptor)
      .filter { it.contains(groupSubstring, ignoreCase = true) }
      .flatMap { group ->
        indexer.artifacts(eelDescriptor, group)
          .filter { it.contains(artifactSubstring, ignoreCase = true) }
          .flatMap { artifact ->
            indexer.versions(eelDescriptor, group, artifact)
              .filter { it.contains(versionSubstring, ignoreCase = true) }
              .map { version -> DependencyCompletionResult(group, artifact, version) }
          }
      }
  }

  override suspend fun getGroups(request: DependencyGroupCompletionRequest): List<String> {
    val artifactFilter = request.artifact
    val eelDescriptor = request.context.eelDescriptor
    val indexer = service<GradleLocalRepositoryIndexer>()
    return indexer.groups(eelDescriptor)
      .filter { it.contains(request.groupPrefix, ignoreCase = true) }
      .filter { artifactFilter.isEmpty() || indexer.artifacts(eelDescriptor, it).contains(artifactFilter) }
  }

  override suspend fun getArtifacts(request: DependencyArtifactCompletionRequest): List<String> {
    val eelDescriptor = request.context.eelDescriptor
    val indexer = service<GradleLocalRepositoryIndexer>()
    return indexer.artifacts(eelDescriptor, request.group)
      .filter { it.contains(request.artifactPrefix, ignoreCase = true) }
  }

  override suspend fun getVersions(request: DependencyVersionCompletionRequest): List<String> {
    val eelDescriptor = request.context.eelDescriptor
    val indexer = service<GradleLocalRepositoryIndexer>()
    return indexer.versions(eelDescriptor, request.group, request.artifact)
      .filter { it.contains(request.versionPrefix, ignoreCase = true) }
  }
}
