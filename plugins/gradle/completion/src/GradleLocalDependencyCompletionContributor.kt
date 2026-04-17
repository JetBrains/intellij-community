// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion

import com.intellij.gradle.completion.indexer.GradleLocalRepositoryIndexer
import com.intellij.openapi.components.service
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContributor
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.util.registry.Registry
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.util.GradleConstants

@ApiStatus.Internal
class GradleLocalDependencyCompletionContributor : DependencyCompletionContributor {

  override val source: DependencyCompletionContributionSource = DependencyCompletionContributionSource.LOCAL

  override val buildSystemId: ProjectSystemId = GradleConstants.SYSTEM_ID

  override fun isEnabled(): Boolean {
    return Registry.`is`("gradle.dependency.completion.contributor.local")
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
              .map { version ->
                DependencyCompletionResult(group, artifact, version, source = DependencyCompletionContributionSource.LOCAL)
              }
          }
      }.toSet()
    val matchesOnArtifact = indexer.artifacts(eelDescriptor)
      .filter { it.contains(substring, ignoreCase = true) }
      .flatMap { artifact ->
        indexer.groups(eelDescriptor, artifact)
          .flatMap { group ->
            indexer.versions(eelDescriptor, group, artifact)
              .map { version ->
                DependencyCompletionResult(group, artifact, version, source = DependencyCompletionContributionSource.LOCAL)
              }
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
              .map { version ->
                DependencyCompletionResult(group, artifact, version, source = DependencyCompletionContributionSource.LOCAL)
              }
          }
      }
  }

  override suspend fun getGroups(request: DependencyGroupCompletionRequest): List<DependencyPartCompletionResult> {
    val artifactFilter = request.artifact
    val eelDescriptor = request.context.eelDescriptor
    val indexer = service<GradleLocalRepositoryIndexer>()
    val results = indexer.groups(eelDescriptor)
      .filter { it.contains(request.groupPrefix, ignoreCase = true) }
      .filter { artifactFilter.isEmpty() || indexer.artifacts(eelDescriptor, it).contains(artifactFilter) }
    return results.map { DependencyPartCompletionResult(it, source = DependencyCompletionContributionSource.LOCAL) }
  }

  override suspend fun getArtifacts(request: DependencyArtifactCompletionRequest): List<DependencyPartCompletionResult> {
    val eelDescriptor = request.context.eelDescriptor
    val indexer = service<GradleLocalRepositoryIndexer>()
    val results = indexer.artifacts(eelDescriptor, request.group)
      .filter { it.contains(request.artifactPrefix, ignoreCase = true) }
    return results.map { DependencyPartCompletionResult(it, source = DependencyCompletionContributionSource.LOCAL) }
  }

  override suspend fun getVersions(request: DependencyVersionCompletionRequest): List<DependencyPartCompletionResult> {
    val eelDescriptor = request.context.eelDescriptor
    val indexer = service<GradleLocalRepositoryIndexer>()
    val results = indexer.versions(eelDescriptor, request.group, request.artifact)
      .filter { it.contains(request.versionPrefix, ignoreCase = true) }
    return results.map { DependencyPartCompletionResult(it, source = DependencyCompletionContributionSource.LOCAL) }
  }
}