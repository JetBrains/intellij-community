// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin.artifacts

import com.intellij.openapi.application.PathManager
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader.downloadArtifactForIdeFromSources
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinMavenUtils
import java.io.File

object TestKotlinArtifacts {
    private fun getLibraryFile(groupId: String, artifactId: String, libraryFileName: String): File {
        val version = KotlinMavenUtils.findLibraryVersion(libraryFileName)
            ?: error("Cannot find library version for library $libraryFileName")

        return KotlinMavenUtils.findArtifactOrFail(groupId, artifactId, version).toFile()
    }

    private fun getJar(artifactId: String) =
        downloadArtifactForIdeFromSources("kotlinc_kotlin_stdlib.xml", artifactId)
    private fun getSourcesJar(artifactId: String) =
        downloadArtifactForIdeFromSources("kotlinc_kotlin_stdlib.xml", artifactId, suffix = "-sources.jar")

    val kotlinStdlibCommon: File by lazy { getJar("kotlin-stdlib-common") }
    val kotlinStdlibCommonSources: File by lazy { getSourcesJar("kotlin-stdlib-common") }
    val kotlinScriptingCommon: File by lazy { getJar("kotlin-scripting-common") }
    val kotlinScriptingJvm: File by lazy { getJar("kotlin-scripting-jvm") }
    val kotlinScriptingCompiler: File by lazy { getJar("kotlin-scripting-compiler") }
    val kotlinScriptingCompilerImpl: File by lazy { getJar("kotlin-scripting-compiler-impl") }

    val jsr305: File by lazy { getLibraryFile("com.google.code.findbugs", "jsr305", "jsr305.xml") }
    val junit3: File by lazy { getLibraryFile("junit", "junit", "JUnit3.xml") }

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
        downloadArtifactForIdeFromSources(
            libraryFileName = "kotlinc_kotlin_jps_plugin_tests.xml",
            artifactId = "js-ir-runtime-for-ide",
            suffix = ".klib"
        )
    }

    @JvmStatic
    fun jpsPluginTestData(jpsTestDataPath: String): String {
        return jpsPluginTestDataDir.resolve(jpsTestDataPath).canonicalPath
    }

    private fun downloadAndUnpack(libraryFileName: String, artifactId: String, dirName: String): File {
        val jar = downloadArtifactForIdeFromSources(libraryFileName, artifactId)
        return LazyZipUnpacker(File(PathManager.getCommunityHomePath()).resolve("out").resolve(dirName)).lazyUnpack(jar)
    }
}
