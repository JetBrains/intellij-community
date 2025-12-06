// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.shared.completion

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.idea.completion.api.*
import org.jetbrains.plugins.gradle.service.cache.GradleLocalRepositoryIndex

private class KotlinGradleDependencyCompletionContributor : DependencyCompletionContributor {

    override fun isApplicable(context: DependencyCompletionContext): Boolean {
        return context is GradleDependencyCompletionContext
    }

    override suspend fun search(request: DependencyCompletionRequest): List<DependencyCompletionResult> {
        val searchString = request.searchString.trim()

        val parts = searchString.split(":")
        if (parts.isEmpty()) return emptyList()

        val groupPrefix = parts.getOrNull(0).orEmpty()
        val artifactPrefix = parts.getOrNull(1).orEmpty()
        val versionPrefix = parts.getOrNull(2).orEmpty()

        return GradleLocalRepositoryIndex.groups()
            .asSequence()
            .startsWithPrefix(groupPrefix)
            .flatMap { group -> GradleLocalRepositoryIndex.artifacts(group)
                .asSequence()
                .startsWithPrefix(artifactPrefix)
                .flatMap { artifact -> GradleLocalRepositoryIndex.versions(group, artifact)
                    .asSequence()
                    .startsWithPrefix(versionPrefix)
                    .map { version -> DependencyCompletionResult(group, artifact, version) }
                }
            }.toList()
    }

    private fun Sequence<String>.startsWithPrefix(prefix: String) = if (prefix.isEmpty()) this else filter { it.startsWith(prefix) }

    override suspend fun getGroups(request: DependencyGroupCompletionRequest): List<String> {
        val artifactFilter = request.artifact
        val groups = GradleLocalRepositoryIndex.groups()
            .asSequence()
            .filter { it.startsWith(request.groupPrefix) }
            .filter { artifactFilter.isEmpty() || (GradleLocalRepositoryIndex.artifacts(it).contains(artifactFilter)) }
            .sorted()
            .toList()
        return groups
    }

    override suspend fun getArtifacts(request: DependencyArtifactCompletionRequest): List<String> {
        val artifacts = GradleLocalRepositoryIndex.artifacts(request.group)
            .asSequence()
            .filter { it.startsWith(request.artifactPrefix) }
            .sorted()
            .toList()
        return artifacts
    }

    override suspend fun getVersions(request: DependencyVersionCompletionRequest): List<String> {
        val versions = GradleLocalRepositoryIndex.versions(request.group, request.artifact)
            .asSequence()
            .filter { it.startsWith(request.versionPrefix) }
            .sortedDescending()
            .toList()
        return versions
    }
}
