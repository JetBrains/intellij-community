// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.JDOMUtil
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.utils.yieldIfNotNull
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import com.google.common.base.Function as GuavaFunction

object KotlinMavenUtils {
    /**
     * Deducts main binary artifact version in a given IntelliJ IDEA project library.
     * This function works only when the IDE is run from sources (for instance, in tests), as in production there is no '.idea' directory.
     */
    fun findLibraryVersion(libraryFileName: String): String {
        val librariesDir = Paths.get(PathManager.getHomePath(), ".idea/libraries")
        val libraryFile = librariesDir.resolve(libraryFileName)
        val libraryDocument = JDOMUtil.load(libraryFile)

        val libraryElement = libraryDocument.getChild("library")
            ?: error("Invalid library file: $libraryFile")

        if (libraryElement.getAttributeValue("type") == "repository") {
            val propertiesElement = libraryElement.getChild("properties")
            val mavenId = propertiesElement?.getAttributeValue("maven-id")
            if (!mavenId.isNullOrEmpty()) {
                val mavenIdChunks = mavenId.split(':')
                if (mavenIdChunks.size == 3) {
                    return mavenIdChunks[2]
                }
            }
        }

        // Assume the library has the Maven-like repository path, yet it's not a "repository" library
        // This is useful for Kotlin cooperative mode where artifacts are placed in '<kotlinRepo>/build/repo'
        val firstRootUrl = libraryElement.getChild("CLASSES")?.getChild("root")?.getAttributeValue("url")?.substringBefore("!/")
        if (!firstRootUrl.isNullOrEmpty()) {
            val urlChunks = firstRootUrl.split('/')
            if (urlChunks.size >= 3) {
                val fileName = urlChunks[urlChunks.lastIndex]
                val version = urlChunks[urlChunks.lastIndex - 1]
                val artifactId = urlChunks[urlChunks.lastIndex - 2]
                if (fileName.startsWith("$artifactId-$version") && fileName.endsWith(".jar", ignoreCase = true)) {
                    return version
                }
            }
        }

        error("Can't get '$libraryFileName' version")
    }

    /**
     * Returns the single non-classified binary artifact with given coordinates, or throws an exception if the artifact file is not found.
     * This function works both in production and when the IDEA is run from sources (for instance, in tests).
     */
    fun findArtifactOrFail(groupId: String, artifactId: String, version: String): Path {
        return findArtifact(groupId, artifactId, version)
            ?: error("Artifact $groupId:$artifactId:$version not found")
    }

    /**
     * Returns the single non-classified binary artifact with given coordinates, or `null` if the artifact file is not found.
     * This function works both in production and when the IntelliJ IDEA is run from sources (for instance, in tests).
     */
    fun findArtifact(groupId: String, artifactId: String, version: String, suffix: String = ".jar"): Path? {
        return KotlinMavenArtifactFinder.instance.findArtifact(groupId, artifactId, version, suffix)
    }
}

internal abstract class KotlinMavenArtifactFinder {
    companion object {
        val instance: KotlinMavenArtifactFinder
            get() = when {
                isRunningFromSources -> SourcesKotlinMavenArtifactFinder
                else -> ProductionKotlinMavenArtifactFinder
            }
    }

    protected abstract val repositories: Sequence<Path>

    fun findArtifact(groupId: String, artifactId: String, version: String, suffix: String): Path? {
        for (repository in repositories) {
            val artifact = repository.resolve(groupId.replace(".", "/"))
                .resolve(artifactId)
                .resolve(version)
                .resolve("$artifactId-$version$suffix")
                .takeIf { it.isRegularFile() }

            if (artifact != null) {
                return artifact
            }
        }

        return null
    }
}

private object ProductionKotlinMavenArtifactFinder : KotlinMavenArtifactFinder() {
    override val repositories: Sequence<Path> = sequence {
        yield(JarRepositoryManager.getLocalRepositoryPath().toPath())
    }
}

private object SourcesKotlinMavenArtifactFinder : KotlinMavenArtifactFinder() {
    override val repositories: Sequence<Path> = sequence {
        yieldIfNotNull(repositoryForKotlinCompiler)
        yieldIfNotNull(repositoryForLibraries)
    }

    private val repositoryForKotlinCompiler: Path? by lazy { findMavenRepository(KtElement::class.java, KOTLIN_MAVEN_GROUP_ID) }
    private val repositoryForLibraries: Path? by lazy { findMavenRepository(GuavaFunction::class.java, "com.google.guava") }

    private fun findMavenRepository(libraryClass: Class<*>, groupId: String): Path? {
        val compilerArtifactJarPathString = PathManager.getJarPathForClass(libraryClass) ?: return null
        val compilerArtifactJar = Path.of(compilerArtifactJarPathString)
        val versionDir = compilerArtifactJar.parent ?: return null
        val artifactDir = versionDir.parent ?: return null
        val repositoryDir = groupId.split('.').fold<String, Path?>(artifactDir.parent) { path, _ -> path?.parent } ?: return null
        if (compilerArtifactJar.nameWithoutExtension == "${artifactDir.name}-${versionDir.name}") {
            return repositoryDir
        }
        return null
    }
}