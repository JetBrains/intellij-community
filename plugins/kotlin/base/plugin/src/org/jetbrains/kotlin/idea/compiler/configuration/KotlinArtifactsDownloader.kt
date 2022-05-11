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
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_DIST_ARTIFACT_ID
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

    fun isKotlinDistInitialized(version: String): Boolean {
        if (IdeKotlinVersion.get(version) == KotlinPluginLayout.instance.standaloneCompilerVersion) {
            return true
        }
        val jar = KotlinMavenUtils.findArtifact(KOTLIN_MAVEN_GROUP_ID, KOTLIN_DIST_ARTIFACT_ID, version)?.toFile() ?: return false
        return getLazyDistUnpacker(version).isUpToDate(jar)
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
            onError(failedToDownloadMavenArtifact(project, KOTLIN_DIST_ARTIFACT_ID, jpsVersion))
        }

        return true
    }

    fun lazyDownloadAndUnpackKotlincDist(
        project: Project,
        version: String,
        indicator: ProgressIndicator,
    ): File? = lazyDownloadMavenArtifact(
        project,
        KOTLIN_DIST_ARTIFACT_ID,
        version,
        indicator,
        KotlinBasePluginBundle.message("progress.text.downloading.kotlinc.dist"),
    )?.let { jar -> getLazyDistUnpacker(version).lazyUnpack(jar) }

    @Synchronized // Avoid manipulations with the same files from different threads
    fun lazyDownloadMavenArtifact(
        project: Project,
        artifactId: String,
        version: String,
        indicator: ProgressIndicator,
        @Nls indicatorDownloadText: String,
    ): File? {
        if (IdeKotlinVersion.get(version) == KotlinPluginLayout.instance.standaloneCompilerVersion) {
            if (artifactId == KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID) {
                return KotlinPluginLayout.instance.jpsPluginJar
            }
            if (artifactId == KOTLIN_DIST_ARTIFACT_ID) {
                return KotlinPluginLayout.instance.kotlinc
            }
        }
        fun getExpectedMavenArtifactPath() = KotlinMavenUtils.findArtifact(KOTLIN_MAVEN_GROUP_ID, artifactId, version)?.toFile()
        getExpectedMavenArtifactPath()?.takeIf { it.exists() }?.let {
            return it
        }
        indicator.text = indicatorDownloadText
        return downloadMavenArtifact(artifactId, version, project, indicator)
            ?.also {
                val expectedMavenArtifactPath = getExpectedMavenArtifactPath()
                check(it == expectedMavenArtifactPath) {
                    "Expected maven artifact path ($expectedMavenArtifactPath) doesn't match actual artifact path ($it)"
                }
            }
    }

    private fun downloadMavenArtifact(
        artifactId: String,
        version: String,
        project: Project,
        indicator: ProgressIndicator,
    ): File? {
        check(isUnitTestMode() || !EventQueue.isDispatchThread()) {
            "Don't call downloadMavenArtifact on UI thread"
        }
        val prop = RepositoryLibraryProperties(
            KOTLIN_MAVEN_GROUP_ID,
            artifactId,
            version,
            /* includeTransitiveDependencies = */false,
            emptyList()
        )

        val repos = getMavenRepos(project)
        val downloadedCompiler = JarRepositoryManager.loadDependenciesSync(
            project,
            prop,
            /* loadSources = */ false,
            /* loadJavadoc = */ false,
            /* copyTo = */ null,
            repos,
            indicator
        )
        if (downloadedCompiler.isEmpty()) {
            return null
        }
        return downloadedCompiler.singleOrNull().let { it ?: error("Expected to download only single artifact") }.file
            .toVirtualFileUrl(VirtualFileUrlManager.getInstance(project)).presentableUrl.let { File(it) }
    }

    private fun getLazyDistUnpacker(version: String) = LazyZipUnpacker(getUnpackedKotlinDistPath(version))

    private fun getMavenRepos(project: Project) =
        RemoteRepositoriesConfiguration.getInstance(project).repositories +
                listOf( // TODO remove once KTI-724 is fixed
                    RemoteRepositoryDescription(
                        "kotlin.ide.plugin.dependencies",
                        "Kotlin IDE Plugin Dependencies",
                        "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies"
                    )
                )

    @Nls
    fun failedToDownloadMavenArtifact(project: Project, artifactId: String, version: String) = KotlinBasePluginBundle.message(
        "failed.to.download.maven.artifact",
        "$KOTLIN_MAVEN_GROUP_ID:$artifactId:$version",
        getMavenRepos(project).joinToString("\n") { it.url }
    )
}
