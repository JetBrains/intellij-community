// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin.artifacts

import com.intellij.openapi.application.PathManager
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
            .copyTo(                                       // Some tests hardcode jar names in their test data
                File(PathManager.getCommunityHomePath())   // (KotlinReferenceTypeHintsProviderTestGenerated).
                    .resolve("out")                        // That's why we need to strip version from the jar name
                    .resolve("kotlin-from-sources-deps-renamed")
                    .resolve("$artifactId-sources.jar"),
                overwrite = true
            )

    @JvmStatic val androidExtensionsRuntime: File by lazy { getJar("android-extensions-compiler-plugin-for-ide") }
    @JvmStatic val kotlinAnnotationsJvm: File by lazy { getJar("kotlin-annotations-jvm") }
    @JvmStatic val kotlinCompiler: File by lazy { getJar("kotlin-compiler") }
    @JvmStatic val kotlinDaemon: File by lazy { getJar("kotlin-daemon") }
    @JvmStatic val kotlinReflect: File by lazy { getJar("kotlin-reflect") }
    @JvmStatic val kotlinScriptRuntime: File by lazy { getJar("kotlin-script-runtime") }
    @JvmStatic val kotlinScriptingCommon: File by lazy { getJar("kotlin-scripting-common") }
    @JvmStatic val kotlinScriptingCompiler: File by lazy { getJar("kotlin-scripting-compiler") }
    @JvmStatic val kotlinScriptingCompilerImpl: File by lazy { getJar("kotlin-scripting-compiler-impl") }
    @JvmStatic val kotlinScriptingJvm: File by lazy { getJar("kotlin-scripting-jvm") }
    @JvmStatic val kotlinStdlib: File by lazy { getJar("kotlin-stdlib") }
    @JvmStatic val kotlinStdlibCommon: File by lazy { getJar("kotlin-stdlib-common") }
    @JvmStatic val kotlinStdlibCommonSources: File by lazy { getSourcesJar("kotlin-stdlib-common") }
    @JvmStatic val kotlinStdlibJdk7: File by lazy { getJar("kotlin-stdlib-jdk7") }
    @JvmStatic val kotlinStdlibJdk8: File by lazy { getJar("kotlin-stdlib-jdk8") }
    @JvmStatic val kotlinStdlibJdk8Sources: File by lazy { getSourcesJar("kotlin-stdlib-jdk8") }
    @JvmStatic val kotlinStdlibJs: File by lazy { getJar("kotlin-stdlib-js") }
    @JvmStatic val kotlinStdlibSources: File by lazy { getSourcesJar("kotlin-stdlib") }
    @JvmStatic val kotlinTest: File by lazy { getJar("kotlin-test") }
    @JvmStatic val kotlinTestJs: File by lazy { getJar("kotlin-test-js") }
    @JvmStatic val kotlinTestJunit: File by lazy { getJar("kotlin-test-junit") }
    @JvmStatic val parcelizeRuntime: File by lazy { getJar("parcelize-compiler-plugin-for-ide") }

    @JvmStatic val trove4j: File by lazy { getLibraryFile("org.jetbrains.intellij.deps", "trove4j", "Trove4j.xml") }
    @JvmStatic val jetbrainsAnnotations: File by lazy { getLibraryFile("org.jetbrains", "annotations", "jetbrains_annotations.xml") }
    @JvmStatic val jsr305: File by lazy { getLibraryFile("com.google.code.findbugs", "jsr305", "jsr305.xml") }
    @JvmStatic val junit3: File by lazy { getLibraryFile("junit", "junit", "JUnit3.xml") }

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
