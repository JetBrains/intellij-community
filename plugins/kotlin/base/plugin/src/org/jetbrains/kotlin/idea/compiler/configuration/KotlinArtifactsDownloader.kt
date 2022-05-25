// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoriesConfiguration
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.toVirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.OLD_KOTLIN_DIST_ARTIFACT_ID
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_DIST_LOCATION_PREFIX
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_MAVEN_GROUP_ID
import org.jetbrains.kotlin.idea.artifacts.LazyZipUnpacker
import org.jetbrains.kotlin.idea.base.plugin.KotlinBasePluginBundle
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import java.awt.EventQueue
import java.io.File

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

        return getLazyDistDownloaderAndUnpacker(version).isUpToDate(version) ||
                getAllIneOneOldFormatLazyDistUnpacker(parsedVersion)?.let { unpacker ->
                    getAllInOneOldFormatPackedDist(parsedVersion.rawVersion)?.let { jar ->
                        unpacker.isUpToDate(jar)
                    }
                } ?: false
    }

    fun getKotlinJpsPluginJarPath(version: String): File {
        if (IdeKotlinVersion.get(version) == KotlinPluginLayout.instance.standaloneCompilerVersion) {
            return KotlinPluginLayout.instance.jpsPluginJar
        }

        return KotlinMavenUtils.findArtifactOrFail(KOTLIN_MAVEN_GROUP_ID, KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID, version).toFile()
    }

    /**
     * @return **true** if all dependencies are ready
     */
    fun downloadMissingJpsPluginDependencies(
        project: Project,
        jpsVersion: String,
        indicator: ProgressIndicator,
        onError: (String) -> Unit,
    ): Boolean {
        lazyDownloadMavenArtifact(
            project,
            KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID,
            jpsVersion,
            indicator,
            KotlinBasePluginBundle.message("progress.text.downloading.kotlin.jps.plugin"),
        ) ?: return false.also {
            onError(failedToDownloadMavenArtifact(project, KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID, jpsVersion))
        }

        lazyDownloadAndUnpackKotlincDist(
            project,
            jpsVersion,
            indicator,
        ) ?: return false.also {
            onError(failedToDownloadMavenArtifact(project, KOTLIN_DIST_FOR_JPS_META_ARTIFACT_ID, jpsVersion))
        }

        return true
    }

    /**
     * @see isKotlinDistInitialized
     */
    @Synchronized // Avoid manipulations with the same files from different threads
    fun lazyDownloadAndUnpackKotlincDist(
        project: Project,
        version: String,
        indicator: ProgressIndicator,
    ): File? {
        val parsedVersion = IdeKotlinVersion.get(version)
        if (parsedVersion == KotlinPluginLayout.instance.standaloneCompilerVersion) {
            return KotlinPluginLayout.instance.kotlinc
        }

        getAllIneOneOldFormatLazyDistUnpacker(parsedVersion)?.let { unpacker ->
            getAllInOneOldFormatPackedDist(version)?.let { packedDist ->
                unpacker.getUnpackedIfUpToDateOrNull(packedDist)?.let { return it }
            }
        }

        val indicatorDownloadText = KotlinBasePluginBundle.message("progress.text.downloading.kotlinc.dist")
        val context = LazyPomAndJarsDownloader.Context(project, indicator, indicatorDownloadText)
        return getLazyDistDownloaderAndUnpacker(version).lazyProduceDist(version, context)
            ?: getAllIneOneOldFormatLazyDistUnpacker(parsedVersion)?.let { unpacker ->
                // Fallback to old "all-in-one jar" artifact (old "all-in-one jar" is available only for Kotlin < 1.7.20)
                lazyDownloadMavenArtifact(project, OLD_KOTLIN_DIST_ARTIFACT_ID, version, indicator, indicatorDownloadText)?.let {
                    unpacker.lazyUnpack(it)
                }
            }
    }

    @Synchronized // Avoid manipulations with the same files from different threads
    fun lazyDownloadMavenArtifact(
        project: Project,
        artifactId: String,
        version: String,
        indicator: ProgressIndicator,
        @Nls indicatorDownloadText: String,
    ): File? {
        if (IdeKotlinVersion.get(version) == KotlinPluginLayout.instance.standaloneCompilerVersion &&
            artifactId == KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID
        ) {
            return KotlinPluginLayout.instance.jpsPluginJar
        }
        fun getExpectedMavenArtifactPath() = KotlinMavenUtils.findArtifact(KOTLIN_MAVEN_GROUP_ID, artifactId, version)?.toFile()
        getExpectedMavenArtifactPath()?.takeIf { it.exists() }?.let {
            return it
        }
        indicator.text = indicatorDownloadText
        val artifacts = downloadMavenArtifacts(artifactId, version, project, indicator)
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
    ): List<File> {
        check(isUnitTestMode() || !EventQueue.isDispatchThread()) {
            "Don't call downloadMavenArtifact on UI thread"
        }
        val prop = RepositoryLibraryProperties(
            "$KOTLIN_MAVEN_GROUP_ID:$artifactId:$version",
            if (artifactIsPom) ArtifactKind.POM.extension else ArtifactKind.ARTIFACT.extension,
            /* includeTransitiveDependencies = */ true,
        )

        val repos = getMavenRepos(project)
        val downloadedArtifacts =
            JarRepositoryManager.loadDependenciesSync(project, prop, false, false, null, repos, indicator)

        return downloadedArtifacts.map { File(it.file.toVirtualFileUrl(VirtualFileUrlManager.getInstance(project)).presentableUrl) }
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

    private fun getAllInOneOldFormatPackedDist(version: String) =
        KotlinMavenUtils.findArtifact(KOTLIN_MAVEN_GROUP_ID, OLD_KOTLIN_DIST_ARTIFACT_ID, version)?.toFile()

    private fun getMavenRepos(project: Project): List<RemoteRepositoryDescription> =
        RemoteRepositoriesConfiguration.getInstance(project).repositories

    @Nls
    fun failedToDownloadMavenArtifact(project: Project, artifactId: String, version: String) = KotlinBasePluginBundle.message(
        "failed.to.download.maven.artifact",
        "$KOTLIN_MAVEN_GROUP_ID:$artifactId:$version",
        getMavenRepos(project).joinToString("\n") { it.url }
    )
}
