// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.openapi.roots.libraries.LibraryKind
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import org.jetbrains.kotlin.idea.framework.JSLibraryKind
import java.io.File

enum class KotlinLibraryData(val libraryName: String, val kind: PersistentLibraryKind<*>?, val classesRoot: File, val sourcesRoot: File) {
    KOTLIN_STDLIB(
        libraryName = "kotlin-stdlib",
        kind = null,
        classesRoot = KotlinArtifacts.instance.kotlinStdlib,
        sourcesRoot = KotlinArtifacts.instance.kotlinStdlibSources
    ),
    KOTLIN_STDLIB_JDK7(
        libraryName = "kotlin-stdlib-jdk7",
        kind = null,
        classesRoot = KotlinArtifacts.instance.kotlinStdlibJdk7,
        sourcesRoot = KotlinArtifacts.instance.kotlinStdlibJdk7Sources
    ),
    KOTLIN_STDLIB_JDK8(
        libraryName = "kotlin-stdlib-jdk8",
        kind = null,
        classesRoot = KotlinArtifacts.instance.kotlinStdlibJdk8,
        sourcesRoot = KotlinArtifacts.instance.kotlinStdlibJdk8Sources
    ),
    KOTLIN_STDLIB_JS(
        libraryName = "kotlin-stdlib-js",
        kind = JSLibraryKind,
        classesRoot = KotlinArtifacts.instance.kotlinStdlibJs,
        sourcesRoot = KotlinArtifacts.instance.kotlinStdlibSources
    )
}