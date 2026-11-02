// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.SystemProperties
import org.eclipse.aether.repository.RemoteRepository
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager
import org.jetbrains.idea.maven.aether.ProgressConsumer
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.extractFile
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * The Kotlin artifacts of the Kotlin JPS builder tests.
 *
 * The versions are fixed here on purpose. The tests do not follow the Kotlin compiler of the IDE, and the project
 * model updater does not change these versions. Update them by hand.
 *
 * The tests download the artifacts from Maven at run time, with the transitive dependencies, as the IDE does for the
 * build process.
 */
object KotlinJpsBuilderTestArtifacts {
    /** The version of `kotlin-jps-plugin-testdata-for-ide`, which holds `jps/jps-plugin/testData` of the Kotlin repository. */
    const val TEST_DATA_VERSION: String = "2.5.20-dev-2285"

    /** The version of the Build Tools API implementation that the tests compile with by default. */
    const val BUILD_TOOLS_IMPL_CURRENT_VERSION: String = "2.5.20-dev-2285"

    /** An earlier version of the Build Tools API implementation. The stable tests check that the builder still works with it. */
    const val BUILD_TOOLS_IMPL_STABLE_VERSION: String = "2.5.20-dev-1532"

    private const val TEST_DATA_ARTIFACT_ID = "kotlin-jps-plugin-testdata-for-ide"
    private const val BUILD_TOOLS_IMPL_ARTIFACT_ID = "kotlin-build-tools-impl"
    private const val KOTLIN_TEST_ARTIFACT_ID = "kotlin-test"

    private const val MAVEN_CENTRAL_URL = "https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2"
    private const val INTELLIJ_DEPENDENCIES_URL =
        "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies"

    /** A complete directory has this file. A directory without it is left from an interrupted run. */
    private const val COMPLETE_MARKER = ".complete"

    private val root: Path by lazy { Path.of(PathManager.getCommunityHomePath(), "out", "kotlin-jps-builder-test-artifacts") }

    private val repositoryManager: ArtifactRepositoryManager by lazy {
        val mavenLocal = File(SystemProperties.getUserHome(), ".m2/repository")
        val repositories = listOf(
            // A cooperative Kotlin snapshot is published to Maven local
            RemoteRepository.Builder("mavenLocal", "default", mavenLocal.toURI().toString()).build(),
            RemoteRepository.Builder("mavenCentral", "default", MAVEN_CENTRAL_URL).build(),
            RemoteRepository.Builder("intellij-dependencies", "default", INTELLIJ_DEPENDENCIES_URL).build(),
        )
        ArtifactRepositoryManager(root.resolve("repository").toFile(), repositories, ProgressConsumer.DEAF)
    }

    /** The `jps/jps-plugin/testData` directory of the Kotlin repository at [TEST_DATA_VERSION]. */
    @JvmStatic
    val testDataDir: Path by lazy {
        val targetDir = root.resolve("testdata-$TEST_DATA_VERSION")
        val jar = resolve(TEST_DATA_ARTIFACT_ID, TEST_DATA_VERSION, transitive = false).single()
        extractFile(jar, targetDir, BuildDependenciesCommunityRoot(Path.of(PathManager.getCommunityHomePath())))
        targetDir.resolve("jps/jps-plugin/testData")
    }

    private val buildToolsImplHomes = ConcurrentHashMap<String, Path>()

    /**
     * The directory with `kotlin-build-tools-impl` of [version] and its run-time classpath. It is the directory that the
     * Kotlin JPS builder expects in `kotlin.jps.build.tools.impl.home`.
     */
    @JvmStatic
    fun buildToolsImplHome(version: String): Path = buildToolsImplHomes.computeIfAbsent(version) {
        val home = root.resolve("build-tools-impl-$version")
        if (home.resolve(COMPLETE_MARKER).exists()) return@computeIfAbsent home

        val jars = resolve(BUILD_TOOLS_IMPL_ARTIFACT_ID, version, transitive = true).filter { it.fileName.toString().endsWith(".jar") }
        check(jars.isNotEmpty()) { "No jars for $KOTLIN_MAVEN_GROUP_ID:$BUILD_TOOLS_IMPL_ARTIFACT_ID:$version" }

        NioFiles.deleteRecursively(home)
        home.createDirectories()
        // The builder loads all jars of the directory, so the Maven file names stay
        for (jar in jars) {
            Files.copy(jar, home.resolve(jar.fileName), StandardCopyOption.REPLACE_EXISTING)
        }
        home.resolve(COMPLETE_MARKER).writeText(jars.joinToString("\n") { it.fileName.toString() })
        home
    }

    /** The `kotlin-test` jar of [version], for the classpath of a test project. */
    @JvmStatic
    fun kotlinTest(version: String): Path = resolve(KOTLIN_TEST_ARTIFACT_ID, version, transitive = false).single()

    @Synchronized
    private fun resolve(artifactId: String, version: String, transitive: Boolean): List<Path> =
        repositoryManager.resolveDependency(KOTLIN_MAVEN_GROUP_ID, artifactId, version, transitive, emptyList()).map { it.toPath() }
}
