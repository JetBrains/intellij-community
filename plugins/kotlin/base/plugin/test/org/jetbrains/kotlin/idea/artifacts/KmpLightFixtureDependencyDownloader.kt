// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.artifacts

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.JarUtil
import com.intellij.util.SystemProperties
import org.eclipse.aether.repository.RemoteRepository
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager
import org.jetbrains.idea.maven.aether.ProgressConsumer
import org.jetbrains.kotlin.konan.file.unzipTo
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.EnumSet

/**
 * Utility test downloader of KMP library parts for manual module configuration without Gradle import.
 * The possible kinds of dependencies are described in [DependencyKind].
 * Use factory methods defined in [KmpAwareLibraryDependency] to specify KMP dependencies for downloading.
 */
object KmpLightFixtureDependencyDownloader {
    private const val MAVEN_CENTRAL_CACHE_REDIRECTOR_URL =
        "https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/"

    /**
     * Download or resolve from local cache a part of a KMP library and transform if necessary.
     *
     * @param kmpDependency description of dependency kind and coordinates.
     * @param directoryForTransformedDependencies an optional output directory for the extracted common metadata library part.
     * If the directory is not specified, a temporary directory will be used.
     * @return a path to a downloaded platform artifact or a downloaded and transformed common metadata artifact; `null` in case of errors.
     */
    fun resolveDependency(kmpDependency: KmpAwareLibraryDependency, directoryForTransformedDependencies: Path? = null): Path? {
        val coordinates = kmpDependency.coordinates
        val kmpDependencyKind = kmpDependency.kind

        val resolvedDependencyArtifact = resolveArtifact(coordinates, kmpDependencyKind.artifactKind) ?: return null

        return when (kmpDependencyKind) {
            DependencyKind.PLATFORM_KLIB, DependencyKind.JAR -> resolvedDependencyArtifact
            DependencyKind.COMMON_METADATA_JAR, DependencyKind.ALL_METADATA_JAR -> {
                check(coordinates.sourceSet != null) { "Unable to resolve a metadata KLIB without a source set, coordinates: $coordinates" }
                resolveSourceSetKlib(resolvedDependencyArtifact, coordinates, directoryForTransformedDependencies)
            }
        }
    }

    private fun resolveArtifact(coordinates: KmpCoordinates, artifactKind: ArtifactKind): Path? {
        val mavenLocalDir = File(SystemProperties.getUserHome(), ".m2/repository")

        val repositories = listOf(
            RemoteRepository.Builder("mavenLocal", "default", "file://" + mavenLocalDir.absolutePath).build(),
            RemoteRepository.Builder("mavenCentral", "default", MAVEN_CENTRAL_CACHE_REDIRECTOR_URL).build(),
        )

        val resolvedDependencyArtifact = ArtifactRepositoryManager(
            mavenLocalDir, repositories, ProgressConsumer.DEAF
        ).resolveDependencyAsArtifact(
            /* groupId = */ coordinates.group,
            /* artifactId = */ coordinates.artifact,
            /* versionConstraint = */ coordinates.version,
            /* artifactKinds = */ EnumSet.of(artifactKind),
            /* includeTransitiveDependencies = */ false,
            /* excludedDependencies = */ emptyList()
        ).singleOrNull()?.file?.toPath() ?: return null

        return resolvedDependencyArtifact
    }

    private fun resolveSourceSetKlib(
        resolvedArtifactPath: Path,
        kmpCoordinates: KmpCoordinates,
        directoryForTransformedDependencies: Path?
    ): Path? {
        val sourceSet = kmpCoordinates.sourceSet
        check(sourceSet != null)
        if (!JarUtil.containsEntry(resolvedArtifactPath.toFile(), sourceSet)) return null

        val transformedLibrariesRoot = directoryForTransformedDependencies
            ?: FileUtilRt.createTempDirectory("kotlinTransformedMetadataLibraries", "").toPath()
        val destination = transformedLibrariesRoot.resolve(kmpCoordinates.toString())

        resolvedArtifactPath.unzipTo(destination, fromSubdirectory = Paths.get("$sourceSet/"))
        return destination
    }
}

enum class DependencyKind(val artifactKind: ArtifactKind) {
    /**
     * JVM JAR with .class files.
     */
    JAR(ArtifactKind.ARTIFACT),

    /**
     * Platform KLIB with target-specific binaries.
     */
    PLATFORM_KLIB(ArtifactKind.KLIB),

    /**
     * Fat composite Kotlin metadata artifact, published by KMP libraries.
     * Contains unpacked common metadata KLIBs with bodiless declaration headers serialized in .knm format
     * One inner KLIB corresponds to one shared source set.
     * Platform (target-specific) binaries and platform source sets' content are not included in this artifact.
     */
    COMMON_METADATA_JAR(ArtifactKind.ARTIFACT),

    /**
     * Same as metadata KLIB, but published as a special `-all` variant.
     * The default variant in such cases is used for a compatibility artifact in a legacy format.
     * This format is used by older KMP libraries, such as kotlin-stdlib and a few others.
     */
    ALL_METADATA_JAR(ArtifactKind.ALL),
}

class KmpCoordinates(
    val group: String,
    val artifact: String,
    val version: String,
    val sourceSet: String?,
) {
    override fun toString(): String {
        val sourceSetIfNotNull = sourceSet?.let { ":$sourceSet" }.orEmpty()
        return "$group:$artifact$sourceSetIfNotNull:$version"
    }
}

class KmpAwareLibraryDependency private constructor(
    val coordinates: KmpCoordinates,
    val kind: DependencyKind,
) {
    companion object {
        // expected format: org.example:some:1.2.3
        fun klib(coordinates: String) = createFromCoordinates(coordinates, DependencyKind.PLATFORM_KLIB)

        // expected format: org.example:some:1.2.3
        fun jar(coordinates: String) = createFromCoordinates(coordinates, DependencyKind.JAR)

        // expected format: org.example:some:commonMain:1.2.3
        fun metadataKlib(coordinates: String) = createFromCoordinates(coordinates, DependencyKind.COMMON_METADATA_JAR)

        // expected format: org.example:some:commonMain:1.2.3
        fun allMetadataJar(coordinates: String) = createFromCoordinates(coordinates, DependencyKind.ALL_METADATA_JAR)

        private fun createFromCoordinates(coordinatesString: String, kind: DependencyKind): KmpAwareLibraryDependency {
            return when (kind) {
                DependencyKind.JAR, DependencyKind.PLATFORM_KLIB -> {
                    val (group, artifact, version) = coordinatesString.split(":").also { check(it.size == 3) }
                    KmpAwareLibraryDependency(KmpCoordinates(group, artifact, version, null), kind)
                }

                DependencyKind.COMMON_METADATA_JAR, DependencyKind.ALL_METADATA_JAR -> {
                    val (group, artifact, sourceSet, version) = coordinatesString.split(":").also { check(it.size == 4) }
                    KmpAwareLibraryDependency(KmpCoordinates(group, artifact, version, sourceSet), kind)
                }
            }
        }
    }
}
