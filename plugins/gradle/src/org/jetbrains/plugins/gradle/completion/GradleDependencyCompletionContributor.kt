// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

    val groupPrefix = parts.getOrNull(0).orEmpty()
    val artifactPrefix = parts.getOrNull(1).orEmpty()
    val versionPrefix = parts.getOrNull(2).orEmpty()

    return when (parts.size) {
      1 -> searchSingle(request, groupPrefix)
      else -> searchFull(request, groupPrefix, artifactPrefix, versionPrefix)
    }
  }

  private fun searchSingle(request: DependencyCompletionRequest, prefix: String): List<DependencyCompletionResult> {
    val eelDescriptor = request.context.eelDescriptor
    val indexer = service<GradleLocalRepositoryIndexer>()
    val results = indexer.artifacts(eelDescriptor)
      .asSequence()
      .startsWithPrefix(prefix)
      .flatMap { artifact ->
        indexer.groups(eelDescriptor, artifact)
          .asSequence()
          .flatMap { group ->
            indexer.versions(eelDescriptor, group, artifact)
              .asSequence()
              .map { version -> DependencyCompletionResult(group, artifact, version) }
          }
      }.toList()
    if (results.isNotEmpty()) return results
    return searchFull(request, prefix, "", "")
  }

  private fun searchFull(
    request: DependencyCompletionRequest,
    groupPrefix: String,
    artifactPrefix: String,
    versionPrefix: String,
  ): List<DependencyCompletionResult> {
    val eelDescriptor = request.context.eelDescriptor
    val indexer = service<GradleLocalRepositoryIndexer>()
    return indexer.groups(eelDescriptor)
      .asSequence()
      .startsWithPrefix(groupPrefix)
      .flatMap { group ->
        indexer.artifacts(eelDescriptor, group)
          .asSequence()
          .startsWithPrefix(artifactPrefix)
          .flatMap { artifact ->
            indexer.versions(eelDescriptor, group, artifact)
              .asSequence()
              .startsWithPrefix(versionPrefix)
              .map { version -> DependencyCompletionResult(group, artifact, version) }
          }
      }.toList()
  }

  private fun Sequence<String>.startsWithPrefix(prefix: String) = if (prefix.isEmpty()) this else filter { it.startsWith(prefix) }

  override suspend fun getGroups(request: DependencyGroupCompletionRequest): List<String> {
    val artifactFilter = request.artifact
    val eelDescriptor = request.context.eelDescriptor
    val indexer = service<GradleLocalRepositoryIndexer>()
    val groups = indexer.groups(eelDescriptor)
      .asSequence()
      .filter { it.startsWith(request.groupPrefix) }
      .filter { artifactFilter.isEmpty() || (indexer.artifacts(eelDescriptor, it).contains(artifactFilter)) }
      .sorted()
      .toList()
    return groups
  }

  override suspend fun getArtifacts(request: DependencyArtifactCompletionRequest): List<String> {
    val eelDescriptor = request.context.eelDescriptor
    val indexer = service<GradleLocalRepositoryIndexer>()
    val artifacts = indexer.artifacts(eelDescriptor, request.group)
      .asSequence()
      .filter { it.startsWith(request.artifactPrefix) }
      .sorted()
      .toList()
    return artifacts
  }

  override suspend fun getVersions(request: DependencyVersionCompletionRequest): List<String> {
    val eelDescriptor = request.context.eelDescriptor
    val indexer = service<GradleLocalRepositoryIndexer>()
    val versions = indexer.versions(eelDescriptor, request.group, request.artifact)
      .asSequence()
      .filter { it.startsWith(request.versionPrefix) }
      .sortedDescending()
      .toList()
    return versions
  }
}
