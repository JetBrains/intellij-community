// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoriesConfiguration
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.io.Decompressor
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.toVirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.kotlin.utils.KotlinPaths
import org.jetbrains.kotlin.utils.KotlinPathsFromHomeDir
import java.io.File

object KotlinPathsProvider {
    const val KOTLIN_MAVEN_GROUP_ID = "org.jetbrains.kotlin"
    const val KOTLIN_DIST_ARTIFACT_ID = "kotlin-dist-for-ide"

    fun getKotlinPaths(version: String): KotlinPaths =
        KotlinPathsFromHomeDir(File(PathManager.getSystemPath(), KOTLIN_DIST_ARTIFACT_ID).resolve(version))

    fun getKotlinPaths(project: Project) =
        KotlinJpsPluginSettings.getInstance(project)?.settings?.version?.let { getKotlinPaths(it) }
            ?: KotlinPathsFromHomeDir(KotlinPluginLayout.getInstance().kotlinc)

    fun lazyUnpackKotlincDist(packedDist: File, version: String): File {
        val destination = getKotlinPaths(version).homePath

        val unpackedDistTimestamp = destination.lastModified()
        val packedDistTimestamp = packedDist.lastModified()
        if (unpackedDistTimestamp != 0L && packedDistTimestamp != 0L && unpackedDistTimestamp >= packedDistTimestamp) {
            return destination
        }
        destination.deleteRecursively()

        Decompressor.Zip(packedDist).extract(destination)
        check(destination.isDirectory)
        return destination
    }

    fun lazyDownloadAndUnpackKotlincDist(
        project: Project,
        version: String,
        indicator: ProgressIndicator,
        beforeDownload: () -> Unit,
        onError: (String) -> Unit,
    ): File? = lazyDownloadMavenArtifact(project, KOTLIN_DIST_ARTIFACT_ID, version, indicator, beforeDownload, onError)
        ?.let { lazyUnpackKotlincDist(it, version) }

    fun lazyDownloadMavenArtifact(
        project: Project,
        artifactId: String,
        version: String,
        indicator: ProgressIndicator,
        beforeDownload: () -> Unit,
        onError: (String) -> Unit,
    ): File? {
        getExpectedMavenArtifactJarPath(artifactId, version).takeIf { it.exists() }?.let {
            return it
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
        beforeDownload()
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
    }

    fun resolveMavenArtifactInMavenRepo(mavenRepo: File, artifactId: String, version: String) =
        mavenRepo.resolve(KOTLIN_MAVEN_GROUP_ID.replace(".", "/"))
            .resolve(artifactId)
            .resolve(version)
            .resolve("$artifactId-$version.jar")

    fun getExpectedMavenArtifactJarPath(artifactId: String, version: String) =
        resolveMavenArtifactInMavenRepo(JarRepositoryManager.getLocalRepositoryPath(), artifactId, version)

}
