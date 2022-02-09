// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.openapi.application.PathManager
import java.io.File

object AdditionalKotlinArtifacts {
    val jetbrainsAnnotations: File by lazy {
        findMavenLibrary("jetbrains_annotations.xml", "org.jetbrains", "annotations")
    }

    val kotlinStdlibCommon: File by lazy {
        findMavenLibrary("kotlin_stdlib_jdk8.xml", "org.jetbrains.kotlin", "kotlin-stdlib-common")
    }

    val kotlinStdlibCommonSources: File by lazy {
        findMavenLibrary("kotlin_stdlib_jdk8.xml", "org.jetbrains.kotlin", "kotlin-stdlib-common", LibraryFileKind.SOURCES)
    }

    val kotlinStdlibMinimalForTest: File by lazy {
        findMavenLibrary(
            "kotlinc_kotlin_stdlib_minimal_for_test_for_ide.xml",
            "org.jetbrains.kotlin",
            "kotlin-stdlib-minimal-for-test-for-ide"
        )
    }

    val jsr305: File by lazy {
        findMavenLibrary("jsr305.xml", "com.google.code.findbugs", "jsr305")
    }

    val junit3: File by lazy {
        findMavenLibrary("JUnit3.xml", "junit", "junit")
    }

    val parcelizeRuntime: File by lazy {
        KotlinArtifacts.instance.kotlincDirectory.resolve("lib/parcelize-runtime.jar").also { check(it.exists()) }
    }

    val androidExtensionsRuntime by lazy {
        KotlinArtifacts.instance.kotlincDirectory.resolve("lib/android-extensions-runtime.jar").also { check(it.exists()) }
    }

    @JvmStatic
    val compilerTestDataDir = run {
        val testDataJar = findMavenLibrary(
            "kotlinc_kotlin_compiler_testdata.xml",
            "org.jetbrains.kotlin",
            "kotlin-compiler-testdata-for-ide"
        )
        lazyUnpackJar(
            testDataJar,
            File(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlinc-testdata"),
            "testData"
        )
    }

    @JvmStatic
    fun compilerTestData(compilerTestDataPath: String): String {
        return compilerTestDataDir.resolve(compilerTestDataPath).canonicalPath
    }
}
