// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.util.SystemProperties
import org.eclipse.aether.repository.RemoteRepository
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager
import org.jetbrains.idea.maven.aether.ProgressConsumer
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import java.io.File
import java.nio.file.Path

object KotlinGradlePluginDownloader {
    private const val kotlinBootstrapRepositoryUrl =
        "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap"

    private const val kotlinIdePluginDependenciesRepositoryUrl =
        "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies"

    private const val mavenCentralRepositoryUrl =
        "https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/"

    fun downloadKotlinGradlePlugin(version: String, downloadOut: Path): Path {
        val mavenLocalDir = File(SystemProperties.getUserHome(), ".m2/repository")

        val repositories = listOf(
            RemoteRepository.Builder("mavenLocal", "default", "file://" + mavenLocalDir.absolutePath).build(),
            RemoteRepository.Builder("mavenCentral", "default", mavenCentralRepositoryUrl).build(),
            RemoteRepository.Builder("bootstrap", "default", kotlinBootstrapRepositoryUrl).build(),
            RemoteRepository.Builder("kotlin-ide-plugin-dependencies", "default", kotlinIdePluginDependenciesRepositoryUrl).build()
        )

        return ArtifactRepositoryManager(
            downloadOut.toFile(), repositories, ProgressConsumer.DEAF
        ).resolveDependency(
            /* groupId = */ KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID,
            /* artifactId = */ "kotlin-gradle-plugin",
            /* version = */ version,
            /* includeTransitiveDependencies = */ false,
            /* excludedDependencies = */ emptyList()
        ).single().toPath()
    }
}