// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.dependencies

import com.intellij.jarRepository.RemoteRepositoryDescription
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import kotlin.script.experimental.api.DependencyCoordinates
import kotlin.script.experimental.api.DependencyRepository
import kotlin.script.experimental.dependencies.impl.DependenciesResolverOptionsName

internal data class ScriptArtifactRepository(
    val id: String,
    val url: String,
    val username: String?,
    val password: String?,
) {
    fun toDescription(): RemoteRepositoryDescription = RemoteRepositoryDescription(id, id, url)
}

internal data class ScriptArtifactsRequest(
    val artifacts: List<String>,
    val options: Map<String, String>,
    val repositories: List<ScriptArtifactRepository>,
)

// Mirrors MavenDependenciesResolver.FORBIDDEN_CHARS: the repository id ends up in local repository file names.
private val FORBIDDEN_CHARS = Regex("[/\\\\:<>\"|?*]")

private fun Map<String, String>.option(name: DependenciesResolverOptionsName): String? = this[name.key]

private fun String?.resolveEnvironmentVariable(): String? = when {
    this == null -> null
    !startsWith("$") -> this
    else -> System.getenv(substring(1))?.takeIf { it.isNotEmpty() }
}

internal fun DependencyRepository.toScriptArtifactRepository(): ScriptArtifactRepository = ScriptArtifactRepository(
    id = options.option(DependenciesResolverOptionsName.MAVEN_REPOSITORY_ID) ?: coordinates.replace(FORBIDDEN_CHARS, "_"),
    url = coordinates,
    username = options.option(DependenciesResolverOptionsName.USERNAME).resolveEnvironmentVariable(),
    password = options.option(DependenciesResolverOptionsName.PASSWORD).resolveEnvironmentVariable(),
)

internal fun DependencyCoordinates.toRequest(repositories: List<DependencyRepository>): ScriptArtifactsRequest = ScriptArtifactsRequest(
    artifacts = artifacts,
    options = options,
    repositories = repositories.map { it.toScriptArtifactRepository() },
)

internal val ScriptArtifactsRequest.isTransitive: Boolean
    get() = when (options.option(DependenciesResolverOptionsName.TRANSITIVE)?.lowercase()) {
        "0", "false", "f", "no", "n" -> false
        else -> true
    }

internal val ScriptArtifactsRequest.unsupportedOptions: List<String>
    get() = buildList {
        options.option(DependenciesResolverOptionsName.CLASSIFIER)?.let { add("${DependenciesResolverOptionsName.CLASSIFIER.key}=$it") }
        options.option(DependenciesResolverOptionsName.PARTIAL_RESOLUTION)
            ?.let { add("${DependenciesResolverOptionsName.PARTIAL_RESOLUTION.key}=$it") }
        options.option(DependenciesResolverOptionsName.KEY_FILE)?.let { add("${DependenciesResolverOptionsName.KEY_FILE.key}=$it") }
    }

// Aether's grammar: <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>
internal fun String.toRepositoryLibraryProperties(transitive: Boolean): RepositoryLibraryProperties? {
    val match = COORDINATES.matchEntire(this) ?: return null
    val (groupId, artifactId, extension, classifier, version) = match.destructured

    if (classifier.isNotEmpty()) return null

    val packaging = extension.ifEmpty { JpsMavenRepositoryLibraryDescriptor.DEFAULT_PACKAGING }
    if (ArtifactKind.find(ArtifactKind.ARTIFACT.classifier, packaging) == null) return null

    return RepositoryLibraryProperties(
        JpsMavenRepositoryLibraryDescriptor(groupId, artifactId, version, packaging, transitive, emptyList())
    )
}

private val COORDINATES = Regex("""([^: ]+):([^: ]+)(?::([^: ]*)(?::([^: ]+))?)?:([^: ]+)""")
