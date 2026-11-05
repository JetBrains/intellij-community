// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.NioFiles
import com.intellij.testFramework.common.BazelTestUtil
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
 * model updater does not change these versions. Update them by hand, together with `kotlin_test_dependencies.bzl`,
 * which pins the same artifacts for Bazel. What each version is, when to update which one and how:
 * [JpsBuilderTests.md](https://github.com/JetBrains/intellij-community/blob/master/plugins/kotlin/docs/JpsBuilderTests.md).
 *
 * Under Bazel, the artifacts come from the run files of the test target: the Bazel entries of `kotlin_test_dependencies.bzl`
 * carry the bucket in their names, `-bootstrap` or `-stable`, so a version bump changes no file name. Outside Bazel,
 * the tests download them from Maven at run time, with the transitive dependencies, as the IDE does for the build
 * process.
 */
object KotlinJpsBuilderTestArtifacts {
    /** The version of `kotlin-jps-plugin-testdata-for-ide`, which holds `jps/jps-plugin/testData` of the Kotlin repository. */
    const val TEST_DATA_VERSION: String = "2.5.20-dev-2285"

    /** The version of the Build Tools API implementation that the tests compile with by default: the bootstrap bucket. */
    const val BUILD_TOOLS_IMPL_BOOTSTRAP_VERSION: String = "2.5.20-dev-2285"

    /** An earlier version of the Build Tools API implementation. The stable tests check that the builder still works with it. */
    const val BUILD_TOOLS_IMPL_STABLE_VERSION: String = "2.5.20-dev-1532"

    private const val TEST_DATA_ARTIFACT_ID = "kotlin-jps-plugin-testdata-for-ide"
    private const val BUILD_TOOLS_IMPL_ARTIFACT_ID = "kotlin-build-tools-impl"
    private const val KOTLIN_TEST_ARTIFACT_ID = "kotlin-test"

    /** The artifacts of the run-time classpath of `kotlin-build-tools-impl` that keep a version of their own. */
    private val BUILD_TOOLS_IMPL_FIXED_CLASSPATH = listOf(
        "annotations-13.0",
        "kotlin-reflect-1.6.10",
        "kotlinx-coroutines-core-jvm-1.8.0",
    )

    /** The artifacts of the run-time classpath of `kotlin-build-tools-impl` that take the version of the implementation. */
    private val BUILD_TOOLS_IMPL_VERSIONED_CLASSPATH = listOf(
        "kotlin-build-tools-api",
        "kotlin-build-tools-cri-impl",
        "kotlin-build-tools-impl",
        "kotlin-compiler-embeddable",
        "kotlin-compiler-runner",
        "kotlin-daemon-client",
        "kotlin-daemon-embeddable",
        "kotlin-stdlib",
        "kotlin-tooling-core",
        "typescript-export-standalone-embeddable",
    )

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
        val jar = if (BazelTestUtil.isUnderBazelTest) {
            bazelFile("$TEST_DATA_ARTIFACT_ID-bootstrap.jar")
        }
        else {
            resolve(TEST_DATA_ARTIFACT_ID, TEST_DATA_VERSION, transitive = false).single()
        }
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

        // Each entry names the Bazel file of the run files and the Maven file name it takes in the home directory.
        val jars: List<Pair<Path, String>> = if (BazelTestUtil.isUnderBazelTest) {
            BUILD_TOOLS_IMPL_FIXED_CLASSPATH.map { bazelFile("$it.jar") to "$it.jar" } +
                    BUILD_TOOLS_IMPL_VERSIONED_CLASSPATH.map { bazelFile("$it-${bazelBucketOf(version)}.jar") to "$it-$version.jar" }
        }
        else {
            resolve(BUILD_TOOLS_IMPL_ARTIFACT_ID, version, transitive = true)
                .filter { it.fileName.toString().endsWith(".jar") }
                .map { it to it.fileName.toString() }
        }
        check(jars.isNotEmpty()) { "No jars for $KOTLIN_MAVEN_GROUP_ID:$BUILD_TOOLS_IMPL_ARTIFACT_ID:$version" }

        NioFiles.deleteRecursively(home)
        home.createDirectories()
        // The builder loads all jars of the directory, and the loader reads the implementation version from the file
        // names, so every jar keeps its Maven file name in the home directory.
        for ((jar, fileName) in jars) {
            Files.copy(jar, home.resolve(fileName), StandardCopyOption.REPLACE_EXISTING)
        }
        home.resolve(COMPLETE_MARKER).writeText(jars.joinToString("\n") { it.second })
        home
    }

    /** The `kotlin-test` jar of [version], for the classpath of a test project. */
    @JvmStatic
    fun kotlinTest(version: String): Path =
        if (BazelTestUtil.isUnderBazelTest) bazelFile("$KOTLIN_TEST_ARTIFACT_ID-${bazelBucketOf(version)}.jar")
        else resolve(KOTLIN_TEST_ARTIFACT_ID, version, transitive = false).single()

    /** The file of [fileName] that Bazel delivered in the run files. The test target lists its label in `data`. */
    private fun bazelFile(fileName: String): Path = BazelTestUtil.getFileFromBazelRuntime("@kotlin_test_deps//:$fileName")

    /**
     * The bucket of [version] in the file names of `kotlin_test_dependencies.bzl`: its Bazel entries carry the bucket,
     * not the version, so a version bump changes no file name.
     */
    private fun bazelBucketOf(version: String): String = when (version) {
        BUILD_TOOLS_IMPL_BOOTSTRAP_VERSION -> "bootstrap"
        BUILD_TOOLS_IMPL_STABLE_VERSION -> "stable"
        else -> error("kotlin-build-tools-impl $version has no bucket: the pinned implementations are $BUILD_TOOLS_IMPL_BOOTSTRAP_VERSION and $BUILD_TOOLS_IMPL_STABLE_VERSION")
    }

    @Synchronized
    private fun resolve(artifactId: String, version: String, transitive: Boolean): List<Path> =
        repositoryManager.resolveDependency(KOTLIN_MAVEN_GROUP_ID, artifactId, version, transitive, emptyList()).map { it.toPath() }
}
