// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.artifacts

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.JarUtil
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.util.SystemProperties
import org.eclipse.aether.repository.RemoteRepository
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager
import org.jetbrains.idea.maven.aether.ProgressConsumer
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.konan.file.unzipTo
import org.jetbrains.kotlin.konan.target.TargetSupportException
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.notExists

/**
 * Utility test downloader of KMP library parts for manual module configuration without Gradle import.
 * The possible kinds of dependencies are described in [DependencyKind].
 * Use factory methods defined in [KmpAwareLibraryDependency] to specify KMP dependencies for downloading.
 */
object KmpLightFixtureDependencyDownloader {
    private const val MAVEN_CENTRAL_CACHE_REDIRECTOR_URL =
        "https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/"
    // For the sliding kotlin-stdlib version
    private const val KOTLIN_IDE_PLUGIN_DEPENDENCIES: String =
        "https://cache-redirector.jetbrains.com/intellij-dependencies/"

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

        return when (coordinates) {
            is MavenKmpCoordinates -> resolveMavenDependency(coordinates, kmpDependencyKind, directoryForTransformedDependencies)
            is NativePrebuiltKmpCoordinates -> resolveKotlinNativePrebuiltDependency(coordinates)
        }
    }

    private fun resolveMavenDependency(
        coordinates: MavenKmpCoordinates,
        kmpDependencyKind: DependencyKind,
        directoryForTransformedDependencies: Path?,
    ): Path? {
        val resolvedDependencyArtifact = resolveArtifact(coordinates, kmpDependencyKind.artifactKind) ?: return null

        return when (kmpDependencyKind) {
            DependencyKind.PLATFORM_KLIB, DependencyKind.JAR -> resolvedDependencyArtifact
            DependencyKind.COMMON_METADATA_JAR, DependencyKind.ALL_METADATA_JAR -> {
                check(coordinates.sourceSet != null) { "Unable to resolve a metadata KLIB without a source set, coordinates: $coordinates" }
                resolveSourceSetKlib(resolvedDependencyArtifact, coordinates, directoryForTransformedDependencies)
            }
            DependencyKind.KOTLIN_NATIVE_PREBUILT -> error("Should not resolve Kotlin/Native prebuilt as Maven dependency")
        }
    }

    private fun resolveKotlinNativePrebuiltDependency(
        coordinates: NativePrebuiltKmpCoordinates,
    ): Path? {
        return try {
            TestKotlinArtifacts.getNativeLib(coordinates.version, library = coordinates.libraryPart).toPath()
        } catch (_: TargetSupportException) {
            // Hack: use the linuxX64 version of K/N distribution, if the host doesn't support it.
            // This is necessary for Linux ARM64 agents, see KT-36871.
            TestKotlinArtifacts.getNativeLib(coordinates.version, platform = "linux-x86_64", library = coordinates.libraryPart).toPath()
        }
    }

    private fun resolveArtifact(coordinates: MavenKmpCoordinates, artifactKind: ArtifactKind): Path? {
        val mavenLocalDir = File(SystemProperties.getUserHome(), ".m2/repository")

        val remoteRepositories = listOf(
            RemoteRepository.Builder("mavenCentral", "default", MAVEN_CENTRAL_CACHE_REDIRECTOR_URL).build(),
            RemoteRepository.Builder("kotlinIdePluginDependencies", "default", KOTLIN_IDE_PLUGIN_DEPENDENCIES).build(),
        )

        val resolvedDependencyArtifact = ArtifactRepositoryManager(
            mavenLocalDir, remoteRepositories, ProgressConsumer.DEAF
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
        kmpCoordinates: MavenKmpCoordinates,
        directoryForTransformedDependencies: Path?
    ): Path? {
        val sourceSet = kmpCoordinates.sourceSet
        check(sourceSet != null)
        if (!JarUtil.containsEntry(resolvedArtifactPath.toFile(), sourceSet)) return null

        val transformedLibrariesRoot = directoryForTransformedDependencies
            ?: FileUtilRt.createTempDirectory("kotlinTransformedMetadataLibraries", "").toPath()
        val destination = transformedLibrariesRoot.resolve(kmpCoordinates.toString().replace(":", "/"))
        if (destination.notExists()) {
            resolvedArtifactPath.unzipTo(destination.createDirectory(), fromSubdirectory = Paths.get("$sourceSet/"))
        }
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

    /**
     * Part of the Kotlin/Native distribution, which is a multi-KLIB with several platform-specific artifacts inside.
     * Artifacts include the Kotlin/Native standard library, shared by all the native targets.
     * At the moment of writing, it is not a Maven publication.
     */
    KOTLIN_NATIVE_PREBUILT(ArtifactKind.KLIB),
}

sealed class KmpCoordinates

data class NativePrebuiltKmpCoordinates(
    val libraryPart: String,
    val version: String,
) : KmpCoordinates()

data class MavenKmpCoordinates(
    val group: String,
    val artifact: String,
    val version: String,
    val sourceSet: String?,
) : KmpCoordinates() {
    override fun toString(): String {
        val sourceSetIfNotNull = sourceSet?.let { ":$sourceSet" }.orEmpty()
        return "$group:$artifact$sourceSetIfNotNull:$version"
    }
}

class KmpAwareLibraryDependency private constructor(
    val coordinates: KmpCoordinates,
    val kind: DependencyKind,
) {
    override fun equals(other: Any?): Boolean {
        if (other !is KmpAwareLibraryDependency) return false
        return kind == other.kind && coordinates == other.coordinates
    }

    override fun hashCode(): Int {
        return 31 * coordinates.hashCode() + kind.hashCode()
    }

    companion object {
        // expected format: org.example:some:1.2.3
        fun klib(coordinates: String) = createFromCoordinates(coordinates, DependencyKind.PLATFORM_KLIB)

        // expected format: org.example:some:1.2.3
        fun jar(coordinates: String) = createFromCoordinates(coordinates, DependencyKind.JAR)

        // expected format: org.example:some:commonMain:1.2.3
        fun metadataKlib(coordinates: String) = createFromCoordinates(coordinates, DependencyKind.COMMON_METADATA_JAR)

        // expected format: org.example:some:commonMain:1.2.3
        fun allMetadataJar(coordinates: String) = createFromCoordinates(coordinates, DependencyKind.ALL_METADATA_JAR)

        // expected format: path/to/library/part:1.2.3
        fun kotlinNativePrebuilt(coordinates: String) = createFromCoordinates(coordinates, DependencyKind.KOTLIN_NATIVE_PREBUILT)

        private fun createFromCoordinates(coordinatesString: String, kind: DependencyKind): KmpAwareLibraryDependency {
            return when (kind) {
                DependencyKind.JAR, DependencyKind.PLATFORM_KLIB -> {
                    val (group, artifact, version) = coordinatesString.split(":").also { check(it.size == 3) }
                    KmpAwareLibraryDependency(MavenKmpCoordinates(group, artifact, version, null), kind)
                }

                DependencyKind.COMMON_METADATA_JAR, DependencyKind.ALL_METADATA_JAR -> {
                    val (group, artifact, sourceSet, version) = coordinatesString.split(":").also { check(it.size == 4) }
                    KmpAwareLibraryDependency(MavenKmpCoordinates(group, artifact, version, sourceSet), kind)
                }

                DependencyKind.KOTLIN_NATIVE_PREBUILT -> {
                    val (library, version) = coordinatesString.split(":").also { check(it.size == 2) }
                    KmpAwareLibraryDependency(NativePrebuiltKmpCoordinates(library, version), kind)
                }
            }
        }
    }
}
