// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.openapi.application.PathManager
import com.intellij.util.xml.dom.readXmlAsModel
import org.eclipse.aether.repository.RemoteRepository
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager
import org.jetbrains.idea.maven.aether.ProgressConsumer
import java.io.File

internal fun findMavenLibrary(library: String, groupId: String, artifactId: String, kind: LibraryFileKind = LibraryFileKind.CLASSES): File {
    val librariesDir = File(PathManager.getHomePath(), ".idea/libraries")
    if (!librariesDir.exists()) {
        throw IllegalStateException("Can't find $librariesDir")
    }

    val libraryFile = File(librariesDir, library)
    if (!libraryFile.exists()) {
        throw IllegalStateException("Can't find library $library")
    }

    val element = libraryFile.inputStream().use(::readXmlAsModel)

    val urlScheme = "jar://"
    val allowedJarLocations = listOf(
        "$urlScheme\$PROJECT_DIR\$/../build/repo",
        "$urlScheme\$MAVEN_REPOSITORY\$"
    )

    val roots = element
        .getChild("library")
        ?.getChild(kind.name)
        ?.children("root")
        .orEmpty()

    val pathInRepository = groupId.replace('.', '/') + '/' + artifactId

    for (root in roots) {
        val url = root.getAttributeValue("url") ?: continue
        for (locationPrefix in allowedJarLocations) {
            if (url.startsWith("$locationPrefix/$pathInRepository/")) {
                require(url.endsWith("!/"))
                val path = url.drop(urlScheme.length).dropLast("!/".length)
                val result = File(substitutePathVariables(path))
                if (!result.exists()) {
                    if (kind == LibraryFileKind.SOURCES) {
                        val version = result.nameWithoutExtension.drop(artifactId.length + 1).dropLast(kind.classifierSuffix.length)
                        return resolveArtifact(groupId, artifactId, version, kind)
                    }

                    throw IllegalStateException("File $result doesn't exist")
                }

                return result
            }
        }
    }

    throw IllegalStateException("${kind.name} root '$pathInRepository' not found in library $library")
}

internal enum class RepoLocation {
    PROJECT_DIR {
        override fun toString(): String {
            return "\$PROJECT_DIR\$"
        }
    },
    MAVEN_REPOSITORY {
        override fun toString(): String {
            return "\$MAVEN_REPOSITORY\$"
        }
    }
}

internal enum class LibraryFileKind(val classifierSuffix: String, val artifactKind: ArtifactKind) {
    CLASSES("", ArtifactKind.ARTIFACT), SOURCES("-sources", ArtifactKind.SOURCES);
}

private val remoteMavenRepositories: List<RemoteRepository> by lazy {
    val jarRepositoriesFile = File(PathManager.getHomePath(), ".idea/jarRepositories.xml")
    val element = jarRepositoriesFile.inputStream().use(::readXmlAsModel)

    val repositories = mutableListOf<RemoteRepository>()

    for (remoteRepo in element.getChild("component")?.children("remote-repository").orEmpty()) {
        fun getOptionValue(key: String): String? {
            val option = remoteRepo.children.find { it.getAttributeValue("name") == key && it.name == "option" } ?: return null
            return option.getAttributeValue("value")?.takeIf { it.isNotEmpty() }
        }

        val id = getOptionValue("id") ?: continue
        val url = getOptionValue("url") ?: continue
        repositories.add(ArtifactRepositoryManager.createRemoteRepository(id, url))
    }

    return@lazy repositories
}

private fun substitutePathVariables(path: String): String {
    if (path.startsWith("${RepoLocation.PROJECT_DIR}/")) {
        val projectDir = File(PathManager.getHomePath())
        return projectDir.resolve(path.drop(RepoLocation.PROJECT_DIR.toString().length + 1)).absolutePath
    } else if (path.startsWith("${RepoLocation.MAVEN_REPOSITORY}/")) {
        val homeDir = System.getProperty("user.home", null)?.let { File(it) }

        fun File.extractLocalRepository(): File? {
            return inputStream()
                .use(::readXmlAsModel).children
                .firstOrNull { it.name == "localRepository" }
                ?.content?.let { File(it) }
        }

        // https://maven.apache.org/settings.html
        val customM2Repo = homeDir?.resolve(".m2")?.resolve("settings.xml")?.takeIf { it.exists() }?.extractLocalRepository()
            ?: System.getenv("M2_HOME")?.let { File(it) }?.resolve("conf")?.resolve("settings.xml")?.takeIf { it.exists() }
                ?.extractLocalRepository()

        val m2Repo = customM2Repo
            ?: homeDir?.resolve(".m2")?.resolve("repository")
            ?: error("Unable to find maven local repo directory")

        return m2Repo.absolutePath + path.drop(RepoLocation.MAVEN_REPOSITORY.toString().length)
    }

    return path
}

private fun resolveArtifact(groupId: String, artifactId: String, version: String, kind: LibraryFileKind): File {
    val repositoryManager = ArtifactRepositoryManager(
            JarRepositoryManager.getLocalRepositoryPath(),
            remoteMavenRepositories,
            ProgressConsumer.DEAF,
            false
    )

    val artifacts = repositoryManager.resolveDependencyAsArtifact(
            groupId, artifactId, version,
            setOf(kind.artifactKind), false, emptyList()
    )

    assert(artifacts.size == 1) { "Single artifact expected for library \"$groupId:$artifactId:$version\", got $artifacts" }
    return artifacts.single().file
}
