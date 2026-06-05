// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoriesConfiguration
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VfsUtilCore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinBasePluginBundle
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_JPS_PLUGIN_PLUGIN_ARTIFACT_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.OLD_KOTLIN_DIST_ARTIFACT_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.LazyZipUnpacker
import org.jetbrains.kotlin.idea.compiler.configuration.LazyKotlinMavenArtifactDownloader.DownloadContext
import org.jetbrains.kotlin.idea.util.application.isHeadlessEnvironment
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import java.awt.EventQueue
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.EnumSet
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.seconds

@Suppress("IO_FILE_USAGE")
object KotlinArtifactsDownloader {
    fun getUnpackedKotlinDistPath(version: String): File =
        if (IdeKotlinVersion.get(version).isStandaloneCompilerVersion) KotlinPluginLayout.kotlinc
        else KOTLIN_DIST_LOCATION_PREFIX.resolve(version)

    fun getUnpackedKotlinDistPath(project: Project) = getUnpackedKotlinDistPath(KotlinJpsPluginSettings.jpsVersion(project))

    /**
     * @see lazyDownloadAndUnpackKotlincDist
     */
    fun isKotlinDistInitialized(version: String): Boolean {
        val parsedVersion = IdeKotlinVersion.get(version)
        if (parsedVersion.isStandaloneCompilerVersion) {
            return true
        }

        return getLazyDistDownloaderAndUnpacker(version).isUpToDate() ||
                getAllIneOneOldFormatLazyDistUnpacker(parsedVersion)?.let { unpacker ->
                    findAllInOneOldFormatPackedDist(parsedVersion.rawVersion)?.let { jar ->
                        unpacker.isUpToDate(jar)
                    }
                } ?: false
    }

    /**
     * @return **true** if all dependencies are ready
     */
    @Synchronized // Avoid manipulations with the same files from different threads
    fun lazyDownloadMissingJpsPluginDependencies(
        project: Project,
        jpsVersion: String,
        indicator: ProgressIndicator,
        onError: (@Nls(capitalization = Nls.Capitalization.Sentence) String) -> Unit,
    ): Boolean {
        val context = LazyKotlinJpsPluginClasspathDownloader.Context(project, indicator)
        val jpsPluginClasspath = LazyKotlinJpsPluginClasspathDownloader(jpsVersion).lazyDownload(context)
        if (jpsPluginClasspath.isEmpty()) {
            onError(failedToDownloadUnbundledJpsMavenArtifact(project, KOTLIN_JPS_PLUGIN_PLUGIN_ARTIFACT_ID, jpsVersion))
            return false
        }

        val unpackedDist = lazyDownloadAndUnpackKotlincDist(project, jpsVersion, indicator)
        if (unpackedDist == null) {
            onError(failedToDownloadUnbundledJpsMavenArtifact(project, KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID, jpsVersion))
            return false
        }

        return true
    }

    /**
     * @see isKotlinDistInitialized
     */
    @Synchronized // Avoid manipulations with the same files from different threads
    fun lazyDownloadAndUnpackKotlincDist(project: Project, version: String, indicator: ProgressIndicator): File? {
        val parsedVersion = IdeKotlinVersion.get(version)
        if (parsedVersion.isStandaloneCompilerVersion) {
            return KotlinPluginLayout.kotlinc
        }

        getAllIneOneOldFormatLazyDistUnpacker(parsedVersion)?.let { unpacker ->
            findAllInOneOldFormatPackedDist(version)?.let { packedDist ->
                unpacker.getUnpackedIfUpToDateOrNull(packedDist)?.let { return it }
            }
        }

        val indicatorDownloadText = KotlinBasePluginBundle.message("progress.text.downloading.kotlinc.dist")
        return getLazyDistDownloaderAndUnpacker(version).lazyProduceDist(DownloadContext(project, indicator, indicatorDownloadText))
            ?: getAllIneOneOldFormatLazyDistUnpacker(parsedVersion)?.let { unpacker ->
                // Fallback to old "all-in-one jar" artifact (old "all-in-one jar" is available only for Kotlin < 1.7.20)
                lazyDownloadOldKotlinDistMavenArtifact(project, version, indicator, indicatorDownloadText)?.let {
                    unpacker.lazyUnpack(it)
                }
            }
    }

    @Synchronized // Avoid manipulations with the same files from different threads
    private fun lazyDownloadOldKotlinDistMavenArtifact(
        project: Project,
        version: String,
        indicator: ProgressIndicator,
        @Nls indicatorDownloadText: String,
    ): File? {
        fun getExpectedMavenArtifactPath() =
            KotlinMavenUtils.findArtifact(KOTLIN_MAVEN_GROUP_ID, OLD_KOTLIN_DIST_ARTIFACT_ID, version)?.toFile()
        getExpectedMavenArtifactPath()?.takeIf { it.exists() }?.let {
            return it
        }
        indicator.text = indicatorDownloadText
        val artifacts = downloadMavenArtifacts(OLD_KOTLIN_DIST_ARTIFACT_ID, version, project, indicator)
        if (artifacts.isEmpty()) {
            return null
        }
        return artifacts.singleOrNull().let { it ?: error("Expected to download only single artifact") }
            .also {
                val expectedMavenArtifactPath = getExpectedMavenArtifactPath()
                check(it == expectedMavenArtifactPath) {
                    "Expected maven artifact path ($expectedMavenArtifactPath) doesn't match actual artifact path ($it)"
                }
            }
    }

    fun downloadMavenArtifacts(
        artifactId: String,
        version: String,
        project: Project,
        indicator: ProgressIndicator,
        artifactIsPom: Boolean = false,
        additionalMavenRepos: List<RemoteRepositoryDescription> = emptyList(),
    ): List<File> {
        check((isUnitTestMode() || isHeadlessEnvironment()) || !EventQueue.isDispatchThread()) {
            "Don't call downloadMavenArtifact on UI thread"
        }

        val excludedDeps = // Since 1.7.20, 'kotlin-dist-for-jps-meta' doesn't depend on broken 'kotlin-annotation-processing'
            if (artifactId == KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID && IdeKotlinVersion.get(version) < IdeKotlinVersion.get("1.7.20")) {
                listOf(
                    // Not existing deps of kotlin-annotation-processing KTI-878
                    "$KOTLIN_MAVEN_GROUP_ID:util",
                    "$KOTLIN_MAVEN_GROUP_ID:cli",
                    "$KOTLIN_MAVEN_GROUP_ID:backend",
                    "$KOTLIN_MAVEN_GROUP_ID:frontend",
                    "$KOTLIN_MAVEN_GROUP_ID:frontend.java",
                    "$KOTLIN_MAVEN_GROUP_ID:plugin-api",
                    "$KOTLIN_MAVEN_GROUP_ID:backend.jvm.entrypoint",
                )
            } else {
                emptyList()
            }

        val prop = RepositoryLibraryProperties(
            JpsMavenRepositoryLibraryDescriptor(
                KOTLIN_MAVEN_GROUP_ID,
                artifactId,
                version,
                if (artifactIsPom) ArtifactKind.POM.extension else ArtifactKind.ARTIFACT.extension,
                true,
                excludedDeps,
            )
        )

        val repos = getMavenRepos(project) + additionalMavenRepos

        return JarRepositoryManager.loadDependenciesSync(project, prop, false, false, null, repos, indicator)
            .map { VfsUtilCore.virtualToIoFile(it.file).canonicalFile }
            .distinct()
    }

    private suspend fun downloadMavenArtifact(groupId: String, artifactId: String, version: String): Path? {
        check(isRunningFromSources) {
            "${::downloadArtifactForIdeFromSources.name} must be called only for IDE running from sources or tests. " +
                    "Use ${::downloadMavenArtifacts.name} when run in production"
        }
        val suffix = ".jar"
        // In cooperative development artifacts are already downloaded and stored in $PROJECT_DIR$/../build/repo
        KotlinMavenUtils.findArtifact(groupId, artifactId, version, suffix)?.let {
            return it
        }

        val fileName = "$artifactId-$version$suffix"
        val artifact = Paths.get(PathManager.getCommunityHomePath())
            .resolve("out")
            .resolve("kotlin-from-sources-deps")
            .resolve(fileName)
            .also { Files.createDirectories(it.parent) }
        val groupPath = groupId.replace(".", "/")
        if (!artifact.exists()) {
            downloadAtomically(artifact, "$groupPath/$artifactId/$version/$fileName")
            check(artifact.exists()) { "$artifact should be downloaded" }
        }

        return artifact
    }

    suspend fun downloadArtifactForIdeFromSources(version: String): Path? =
        downloadMavenArtifact(KOTLIN_MAVEN_GROUP_ID, OLD_KOTLIN_DIST_ARTIFACT_ID, version)

    fun resolveProjectCompilerPluginArtifact(
        project: Project,
        groupId: String,
        artifactId: String,
        version: String,
    ): Path? {
        return try {
            val descriptor = JpsMavenRepositoryLibraryDescriptor(
                groupId,
                artifactId,
                version,
                false,
                emptyList(),
            )
            val repos = getMavenRepos(project)
            val roots = JarRepositoryManager.loadDependenciesSync(
                project, descriptor, EnumSet.of(ArtifactKind.ARTIFACT), repos, /* copyTo = */ null
            ) ?: return null
            roots.firstOrNull()?.let { VfsUtilCore.virtualToIoFile(it.file).canonicalFile.toPath() }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Throwable) {
            COMPILER_PLUGIN_RESOLVE_LOG.warn(
                "Failed to resolve $groupId:$artifactId:$version from project Maven repositories", e,
            )
            null
        }
    }

    private fun getAllIneOneOldFormatLazyDistUnpacker(version: IdeKotlinVersion) =
        if (isAllInOneOldFormatDistFormatAvailable(version)) LazyZipUnpacker(getUnpackedKotlinDistPath(version.rawVersion)) else null

    private fun getLazyDistDownloaderAndUnpacker(version: String) = LazyKotlincDistDownloaderAndUnpacker(version)

    /**
     * Prior to 1.7.20, two formats were possible:
     * - Old "all in one jar" dist [OLD_KOTLIN_DIST_ARTIFACT_ID]
     * - New "dist as all transitive dependencies of one meta pom" format [KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID]
     */
    private fun isAllInOneOldFormatDistFormatAvailable(version: IdeKotlinVersion) = version < IdeKotlinVersion.get("1.7.20")

    private fun findAllInOneOldFormatPackedDist(version: String) =
        KotlinMavenUtils.findArtifact(KOTLIN_MAVEN_GROUP_ID, OLD_KOTLIN_DIST_ARTIFACT_ID, version)?.toFile()

    private fun getMavenRepos(project: Project): List<RemoteRepositoryDescription> =
        RemoteRepositoriesConfiguration.getInstance(project).repositories

    fun failedToDownloadUnbundledJpsMavenArtifact(
        project: Project,
        artifactId: String,
        version: String,
    ): @Nls(capitalization = Nls.Capitalization.Sentence) String {
        require(artifactId == KOTLIN_JPS_PLUGIN_PLUGIN_ARTIFACT_ID || artifactId == KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID) {
            "$artifactId should be either $KOTLIN_JPS_PLUGIN_PLUGIN_ARTIFACT_ID or $KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID"
        }
        val suggestions = listOf(KotlinBasePluginBundle.message("you.can.use.kotlin.compiler.which.is.bundled.into.your.ide")) +
                FailedToDownloadJpsMavenArtifactSuggestedSolutionsContributor.getAllContributors(project).mapNotNull { it.getSuggestion() }
        val suggestion = if (suggestions.size == 1) {
            @Suppress("HardCodedStringLiteral") // Suppress because it's false positive
            suggestions.single()
        } else {
            KotlinBasePluginBundle.message("suggested.solutions", suggestions.joinToString("\n") { "- $it" })
        }
        return KotlinBasePluginBundle.message(
            "failed.to.download.unbundled.jps.maven.artifact",
            "$KOTLIN_MAVEN_GROUP_ID:$artifactId:$version",
            getMavenRepos(project).joinToString("\n") { it.url }.prependIndent()
        ) + "\n\n" + suggestion
    }
}

private val COMPILER_PLUGIN_RESOLVE_LOG = logger<KotlinArtifactsDownloader>()

// downloads and atomically replaces downloaded file with temp file
// do one additional try in 5 seconds after fail
suspend fun downloadAtomically(artifact: Path, artifactCoordinates: String): Path? {
    val tmp = Files.createTempFile(artifact.parent, "${artifact.fileName}.", ".part")

    try {
        val result = retryWithBackOff(times = 3) {
            runInterruptible(Dispatchers.IO) {
                openStream(artifactCoordinates)?.use { input ->
                    Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING)
                    artifact
                }
            }
        }

        if (result == null) return null

        moveReplacing(tmp, artifact)
        return artifact
    } finally {
        withContext(NonCancellable + Dispatchers.IO) {
            Files.deleteIfExists(tmp)
        }
    }
}

suspend fun <T : Any> retryWithBackOff(
    times: Int,
    block: suspend () -> T?
): T? {
    repeat(times - 1) {
        try {
            val result = block()
            if (result != null) return result
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
        delay(5.seconds)
    }

    return try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }
}

private fun openStream(artifactCoordinates: String): InputStream? {
    val urls = listOf(
        "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies/$artifactCoordinates",
        "https://cache-redirector.jetbrains.com/intellij-dependencies/$artifactCoordinates",
        "https://repo1.maven.org/maven2/$artifactCoordinates"
    )

    return urls.firstNotNullOfOrNull { urlString ->
        runCatching { URL(urlString).openStream() }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
    }
}

private fun moveReplacing(from: Path, to: Path) {
    try {
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
    }
}