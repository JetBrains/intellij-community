// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.shared.completion

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.idea.completion.api.*
import org.jetbrains.plugins.gradle.service.execution.gradleUserHomeDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.isDirectory

private class KotlinGradleDependencyCompletionContributor : DependencyCompletionContributor {

    private val group2Artifacts = ConcurrentHashMap<String, MutableSet<String>>()
    private val modules2Versions = ConcurrentHashMap<String, MutableSet<String>>()

    init {
        // TODO change where/when update() is called
        update()
    }

    override fun isApplicable(context: DependencyCompletionContext): Boolean {
        return context is GradleDependencyCompletionContext
    }

    override suspend fun search(request: DependencyCompletionRequest): List<DependencyCompletionResult> {
        val searchString = request.searchString.trim()
        // com.android.tools.idea.gradle.completion.GradleDependencyCompletionContributorTest.testBasicCompletionInGradleKtsFile_qualifiedClosure
        if (searchString.startsWith("androidx")) return emptyList()

        val parts = searchString.split(":")
        if (parts.isEmpty()) return emptyList()

        val groupPrefix = parts.getOrNull(0).orEmpty()
        val artifactPrefix = parts.getOrNull(1).orEmpty()
        val versionPrefix = parts.getOrNull(2).orEmpty()

        return group2Artifacts.keys
            .asSequence()
            .startsWithPrefix(groupPrefix)
            .flatMap { group ->
                group2Artifacts[group].orEmpty()
                    .asSequence()
                    .startsWithPrefix(artifactPrefix)
                    .flatMap { artifact ->
                        modules2Versions["$group:$artifact"].orEmpty()
                            .asSequence()
                            .startsWithPrefix(versionPrefix)
                            .map { version -> DependencyCompletionResult(group, artifact, version) }
                    }
            }.toList()
    }

    private fun Sequence<String>.startsWithPrefix(prefix: String) = if (prefix.isEmpty()) this else this.filter { it.startsWith(prefix) }

    override suspend fun getGroups(request: DependencyGroupCompletionRequest): List<String> {
        val artifactFilter = request.artifact
        val groups = group2Artifacts.keys
            .asSequence()
            .filter { it.startsWith(request.groupPrefix) }
            .filter { artifactFilter.isEmpty() || (group2Artifacts[it]?.contains(artifactFilter) == true) }
            .sorted()
            .toList()
        return groups
    }

    override suspend fun getArtifacts(request: DependencyArtifactCompletionRequest): List<String> {
        val artifacts = group2Artifacts[request.group].orEmpty()
            .asSequence()
            .filter { it.startsWith(request.artifactPrefix) }
            .sorted()
            .toList()
        return artifacts
    }

    override suspend fun getVersions(request: DependencyVersionCompletionRequest): List<String> {
        val module = "${request.group}:${request.artifact}"
        val versions = modules2Versions[module].orEmpty()
            .asSequence()
            .filter { it.startsWith(request.versionPrefix) }
            .sortedDescending()
            .toList()
        return versions
    }

    private fun update() {
        var groupNumber = 0
        var artifactNumber = 0
        var versionNumber = 0
        val startTime = System.currentTimeMillis()
        try {
            // expected structure: <GRADLE_USER_HOME>/caches/modules-2/files-2.1/<group>/<artifact>/<version>/...
            val files21 = gradleUserHomeDir().toPath()
                .resolve("caches")
                .resolve("modules-2")
                .resolve("files-2.1")

            if (!files21.isDirectory()) throw IOException("Cannot find files-2.1 directory at $files21")

            files21.iterateDirectories { groupDir ->
                val group = groupDir.fileName.toString()
                groupDir.iterateDirectories { artifactDir ->
                    val artifact = artifactDir.fileName.toString()
                    group2Artifacts.add(group, artifact)
                    val ga = "$group:$artifact"
                    artifactDir.iterateDirectories { versionDir ->
                        val version = versionDir.fileName.toString()
                        modules2Versions.add(ga, version)
                        versionNumber++
                    }
                    artifactNumber++
                }
                groupNumber++
            }
        } catch (e: IOException) {
            LOG.error(e)
        } finally {
            LOG.info("Gradle GAV index updated in ${System.currentTimeMillis() - startTime} millis")
            LOG.info("Found $groupNumber groups, $artifactNumber artifacts, $versionNumber versions")
        }
    }

    private fun Path.iterateDirectories(action: (Path) -> Unit) =
        Files.list(this).use { stream -> stream.filter(Files::isDirectory).forEach(action) }

    private fun MutableMap<String, MutableSet<String>>.add(key: String, value: String) =
        this.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }.add(value)

    companion object {
        private val LOG = logger<KotlinGradleDependencyCompletionContributor>()
    }
}
