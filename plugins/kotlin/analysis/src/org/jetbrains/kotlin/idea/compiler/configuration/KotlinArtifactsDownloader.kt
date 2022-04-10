// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_DIST_ARTIFACT_ID
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_DIST_LOCATION_PREFIX
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_MAVEN_GROUP_ID
import org.jetbrains.kotlin.idea.artifacts.getMavenArtifactJarPath
import org.jetbrains.kotlin.idea.artifacts.lazyUnpackJar
import java.awt.EventQueue
import java.io.File

object KotlinArtifactsDownloader {
    fun getUnpackedKotlinDistPath(version: String): File =
        if (IdeKotlinVersion.get(version) == KotlinPluginLayout.instance.standaloneCompilerVersion) KotlinPluginLayout.instance.kotlinc
        else KOTLIN_DIST_LOCATION_PREFIX.resolve(version)

    fun getUnpackedKotlinDistPath(project: Project) =
        KotlinJpsPluginSettings.getInstance(project)?.settings?.version?.let { getUnpackedKotlinDistPath(it) }
            ?: KotlinPluginLayout.instance.kotlinc

    fun isKotlinDistInitialized(version: String): Boolean {
        if (IdeKotlinVersion.get(version) == KotlinPluginLayout.instance.standaloneCompilerVersion) {
            return true
        }
        val unpackedTimestamp = getUnpackedKotlinDistPath(version).lastModified()
        val mavenJarTimestamp = getMavenArtifactJarPath(KOTLIN_MAVEN_GROUP_ID, KOTLIN_DIST_ARTIFACT_ID, version).lastModified()
        return unpackedTimestamp != 0L && mavenJarTimestamp != 0L && unpackedTimestamp >= mavenJarTimestamp
    }

    fun getKotlinJpsPluginJarPath(version: String): File {
        if (IdeKotlinVersion.get(version) == KotlinPluginLayout.instance.standaloneCompilerVersion) {
            return KotlinPluginLayout.instance.jpsPluginJar
        }
        return getMavenArtifactJarPath(
            KOTLIN_MAVEN_GROUP_ID,
            KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID,
            version
        )
    }

    fun lazyDownloadAndUnpackKotlincDist(
        project: Project,
        version: String,
        indicator: ProgressIndicator,
        onError: (String) -> Unit,
    ): File? = lazyDownloadMavenArtifact(
        project,
        KOTLIN_DIST_ARTIFACT_ID,
        version,
        indicator,
        KotlinBundle.message("progress.text.downloading.kotlinc.dist"),
        onError
    )?.let { lazyUnpackJar(it, getUnpackedKotlinDistPath(version)) }

    @Synchronized // Avoid manipulations with the same files from different threads
    fun lazyDownloadMavenArtifact(
        project: Project,
        artifactId: String,
        version: String,
        indicator: ProgressIndicator,
        @Nls indicatorDownloadText: String,
        onError: (String) -> Unit,
    ): File? {
        if (IdeKotlinVersion.get(version) == KotlinPluginLayout.instance.standaloneCompilerVersion) {
            if (artifactId == KOTLIN_JPS_PLUGIN_CLASSPATH_ARTIFACT_ID) {
                return KotlinPluginLayout.instance.jpsPluginJar
            }
            if (artifactId == KOTLIN_DIST_ARTIFACT_ID) {
                return KotlinPluginLayout.instance.kotlinc
            }
        }
        val expectedMavenArtifactJarPath = getMavenArtifactJarPath(KOTLIN_MAVEN_GROUP_ID, artifactId, version)
        expectedMavenArtifactJarPath.takeIf { it.exists() }?.let {
            return it
        }
        indicator.text = indicatorDownloadText
        return downloadMavenArtifact(artifactId, version, project, indicator, onError, expectedMavenArtifactJarPath)
    }

    private fun downloadMavenArtifact(
        artifactId: String,
        version: String,
        project: Project,
        indicator: ProgressIndicator,
        onError: (String) -> Unit,
        expectedMavenArtifactJarPath: File
    ): File? {
        check(!EventQueue.isDispatchThread()) {
            "Don't call downloadMavenArtifact on UI thread"
        }
        val prop = RepositoryLibraryProperties(
            KOTLIN_MAVEN_GROUP_ID,
            artifactId,
            version,
            /* includeTransitiveDependencies = */false,
            emptyList()
        )

        val repos = RemoteRepositoriesConfiguration.getInstance(project).repositories +
                listOf( // TODO remove once KTI-724 is fixed
                    RemoteRepositoryDescription(
                        "kotlin.ide.plugin.dependencies",
                        "Kotlin IDE Plugin Dependencies",
                        "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies"
                    )
                )
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
            with(prop) {
                onError("Failed to download maven artifact ($groupId:$artifactId${getVersion()}). " +
                                "Searched the artifact in following repos:\n" +
                                repos.joinToString("\n") { it.url })
            }
            return null
        }
        return downloadedCompiler.singleOrNull().let { it ?: error("Expected to download only single artifact") }.file
            .toVirtualFileUrl(VirtualFileUrlManager.getInstance(project)).presentableUrl.let { File(it) }
            .also {
                check(it == expectedMavenArtifactJarPath) {
                    "Expected maven artifact path ($expectedMavenArtifactJarPath) doesn't match actual artifact path ($it)"
                }
            }
    }
}
