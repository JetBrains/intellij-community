// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.openapi.application.PathManager
import com.intellij.util.io.exists
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_MAVEN_GROUP_ID
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinMavenUtils
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

object AdditionalKotlinArtifacts {
    private fun getLibraryFile(groupId: String, artifactId: String, libraryFileName: String): File {
        val version = KotlinMavenUtils.findLibraryVersion(libraryFileName)
            ?: error("Cannot find library version for library $libraryFileName")

        return KotlinMavenUtils.findArtifactOrFail(groupId, artifactId, version).toFile()
    }

    val kotlinStdlibCommon: File by lazy {
        getLibraryFile(KOTLIN_MAVEN_GROUP_ID, "kotlin-stdlib-common", "kotlin_stdlib_jdk8.xml")
    }

    val kotlinStdlibCommonSources: File by lazy {
        getLibraryFile(KOTLIN_MAVEN_GROUP_ID, "kotlin-stdlib-common", "kotlin_stdlib_jdk8.xml")
    }

    val jsr305: File by lazy {
        getLibraryFile("com.google.code.findbugs", "jsr305", "jsr305.xml")
    }

    val junit3: File by lazy {
        getLibraryFile("junit", "junit", "JUnit3.xml")
    }

    @JvmStatic
    val compilerTestDataDir: File by lazy {
        downloadAndUnpack(
            libraryFileName = "kotlinc_kotlin_compiler_cli.xml",
            artifactId = "kotlin-compiler-testdata-for-ide",
            dirName = "kotlinc-testdata-2",
        )
    }

    @JvmStatic
    fun compilerTestData(compilerTestDataPath: String): String {
        return compilerTestDataDir.resolve(compilerTestDataPath).canonicalPath
    }

    @JvmStatic
    val jpsPluginTestDataDir: File by lazy {
        downloadAndUnpack(
            libraryFileName = "kotlinc_kotlin_jps_plugin_tests.xml",
            artifactId = "kotlin-jps-plugin-testdata-for-ide",
            dirName = "kotlinc-jps-testdata",
        )
    }

    @JvmStatic
    val jsIrRuntimeDir: File by lazy {
        downloadArtifact(libraryFileName = "kotlinc_kotlin_jps_plugin_tests.xml", artifactId = "js-ir-runtime-for-ide", extension = "klib")
    }

    @JvmStatic
    fun jpsPluginTestData(jpsTestDataPath: String): String {
        return jpsPluginTestDataDir.resolve(jpsTestDataPath).canonicalPath
    }

    private fun downloadAndUnpack(libraryFileName: String, artifactId: String, dirName: String): File {
        val jar = downloadArtifact(libraryFileName, artifactId)
        return LazyZipUnpacker(File(PathManager.getCommunityHomePath()).resolve("out").resolve(dirName)).lazyUnpack(jar)
    }

    fun downloadArtifact(libraryFileName: String, artifactId: String, extension: String = "jar"): File {
        val version = KotlinMavenUtils.findLibraryVersion(libraryFileName) ?: error("Can't get '$libraryFileName' version")

        // In cooperative development artifacts are already downloaded and stored in $PROJECT_DIR$/../build/repo
        KotlinMavenUtils.findArtifact(KOTLIN_MAVEN_GROUP_ID, artifactId, version)?.let {
            return it.toFile()
        }

        val jar = Paths.get(PathManager.getCommunityHomePath()).resolve("out").resolve("$artifactId-$version.$extension").also {
            Files.createDirectories(it.parent)
        }

        if (!jar.exists()) {
            val stream = URL(
                "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies/" +
                        "org/jetbrains/kotlin/$artifactId/$version/$artifactId-$version.$extension"
            ).openStream()
            Files.copy(stream, jar)
            check(jar.exists()) { "$jar should be downloaded" }
        }

        return jar.toFile()
    }
}
