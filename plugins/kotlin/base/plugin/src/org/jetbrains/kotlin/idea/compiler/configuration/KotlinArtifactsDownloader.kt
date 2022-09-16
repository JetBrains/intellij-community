// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoriesConfiguration
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.toVirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_DIST_LOCATION_PREFIX
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_JPS_PLUGIN_PLUGIN_ARTIFACT_ID
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_MAVEN_GROUP_ID
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.OLD_KOTLIN_DIST_ARTIFACT_ID
import org.jetbrains.kotlin.idea.artifacts.LazyZipUnpacker
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinBasePluginBundle
import org.jetbrains.kotlin.idea.compiler.configuration.LazyKotlinMavenArtifactDownloader.DownloadContext
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import java.awt.EventQueue
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

object KotlinArtifactsDownloader {
    fun getUnpackedKotlinDistPath(version: String): File =
        if (IdeKotlinVersion.get(version) == KotlinPluginLayout.instance.standaloneCompilerVersion) KotlinPluginLayout.instance.kotlinc
        else KOTLIN_DIST_LOCATION_PREFIX.resolve(version)

    fun getUnpackedKotlinDistPath(project: Project) =
        KotlinJpsPluginSettings.jpsVersion(project)?.let { getUnpackedKotlinDistPath(it) } ?: KotlinPluginLayout.instance.kotlinc

    /**
     * @see lazyDownloadAndUnpackKotlincDist
     */
    fun isKotlinDistInitialized(version: String): Boolean {
        val parsedVersion = IdeKotlinVersion.get(version)
        if (parsedVersion == KotlinPluginLayout.instance.standaloneCompilerVersion) {
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
        onError: (String) -> Unit,
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
        if (parsedVersion == KotlinPluginLayout.instance.standaloneCompilerVersion) {
            return KotlinPluginLayout.instance.kotlinc
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
        check(isUnitTestMode() || !EventQueue.isDispatchThread()) {
            "Don't call downloadMavenArtifact on UI thread"
        }

        val excludedDeps =
            if (artifactId == KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID && IdeKotlinVersion.get(version) < IdeKotlinVersion.get("1.7.20")) {
                listOf( // Not existing deps of kotlin-annotation-processing KTI-878
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
            .map { File(it.file.toVirtualFileUrl(VirtualFileUrlManager.getInstance(project)).presentableUrl).canonicalFile }
            .distinct()
    }

    @JvmOverloads
    fun downloadArtifactForIdeFromSources(artifactId: String, version: String, suffix: String = ".jar"): File {
        check(isRunningFromSources) {
            "${::downloadArtifactForIdeFromSources.name} must be called only for IDE running from sources or tests. " +
                    "Use ${::downloadMavenArtifacts.name} when run in production"
        }

        // In cooperative development artifacts are already downloaded and stored in $PROJECT_DIR$/../build/repo
        KotlinMavenUtils.findArtifact(KOTLIN_MAVEN_GROUP_ID, artifactId, version, suffix)?.let {
            return it.toFile()
        }

        val fileName = "$artifactId-$version$suffix"
        val artifact = Paths.get(PathManager.getCommunityHomePath())
            .resolve("out")
            .resolve("kotlin-from-sources-deps")
            .resolve(fileName)
            .also { Files.createDirectories(it.parent) }

        if (!artifact.exists()) {
            val stream = URL(
                "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies/" +
                        "org/jetbrains/kotlin/$artifactId/$version/$fileName"
            ).openStream()
            Files.copy(stream, artifact)
            check(artifact.exists()) { "$artifact should be downloaded" }
        }

        return artifact.toFile()
    }

    private fun getAllIneOneOldFormatLazyDistUnpacker(version: IdeKotlinVersion) =
        if (isAllInOneOldFormatDistFormatAvailable(version)) LazyZipUnpacker(getUnpackedKotlinDistPath(version.rawVersion)) else null

    private fun getLazyDistDownloaderAndUnpacker(version: String) = LazyKotlincDistDownloaderAndUnpacker(version)

    /**
     * Prior to 1.7.20, two formats were possible:
     * - Old "all in one jar" dist [KotlinArtifacts.OLD_KOTLIN_DIST_ARTIFACT_ID]
     * - New "dist as all transitive dependencies of one meta pom" format [KotlinArtifacts.KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID]
     */
    private fun isAllInOneOldFormatDistFormatAvailable(version: IdeKotlinVersion) = version < IdeKotlinVersion.get("1.7.20")

    private fun findAllInOneOldFormatPackedDist(version: String) =
        KotlinMavenUtils.findArtifact(KOTLIN_MAVEN_GROUP_ID, OLD_KOTLIN_DIST_ARTIFACT_ID, version)?.toFile()

    private fun getMavenRepos(project: Project): List<RemoteRepositoryDescription> =
        RemoteRepositoriesConfiguration.getInstance(project).repositories

    @Nls
    fun failedToDownloadUnbundledJpsMavenArtifact(project: Project, artifactId: String, version: String): String {
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
