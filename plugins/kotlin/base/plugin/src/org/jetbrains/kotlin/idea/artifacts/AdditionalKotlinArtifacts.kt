// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.openapi.application.PathManager
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_MAVEN_GROUP_ID
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinMavenUtils
import java.io.File

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
        val testDataJar = getLibraryFile(KOTLIN_MAVEN_GROUP_ID, "kotlin-compiler-testdata-for-ide", "kotlinc_kotlin_compiler_testdata.xml")
        lazyUnpackJar(testDataJar, File(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlinc-testdata-2"))
    }

    @JvmStatic
    fun compilerTestData(compilerTestDataPath: String): String {
        return compilerTestDataDir.resolve(compilerTestDataPath).canonicalPath
    }
}