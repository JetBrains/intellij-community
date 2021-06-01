// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.artifacts

import java.io.File

object AdditionalKotlinArtifacts {
    val kotlinStdlibCommon: File by lazy {
        findLibrary(RepoLocation.MAVEN_REPOSITORY, "kotlin_stdlib_jdk8.xml", "org.jetbrains.kotlin", "kotlin-stdlib-common")
    }

    val kotlinStdlibCommonSources: File by lazy {
        findLibrary(RepoLocation.MAVEN_REPOSITORY, "kotlin_stdlib_jdk8.xml", "org.jetbrains.kotlin", "kotlin-stdlib-common", LibraryFileKind.SOURCES)
    }

    val jsr305: File by lazy {
        findLibrary(RepoLocation.MAVEN_REPOSITORY, "jsr305.xml", "com.google.code.findbugs", "jsr305")
    }

    val junit3: File by lazy {
        findLibrary(RepoLocation.MAVEN_REPOSITORY, "JUnit3.xml", "junit", "junit")
    }

    val parcelizeRuntime: File by lazy {
        KotlinArtifacts.instance.kotlincDirectory.resolve("lib/parcelize-runtime.jar").also { check(it.exists()) }
    }

    val androidExtensionsRuntime by lazy {
        KotlinArtifacts.instance.kotlincDirectory.resolve("lib/android-extensions-runtime.jar").also { check(it.exists()) }
    }
}
