// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.platforms

import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import java.io.File

@ApiStatus.Internal
enum class KotlinLibraryData(val libraryName: String, val kind: PersistentLibraryKind<*>?, val classesRoot: File, val sourcesRoot: File) {
    KOTLIN_STDLIB(
      libraryName = "kotlin-stdlib",
      kind = null,
      classesRoot = KotlinArtifacts.kotlinStdlib,
      sourcesRoot = KotlinArtifacts.kotlinStdlibSources
    ),
    KOTLIN_STDLIB_JDK7(
      libraryName = "kotlin-stdlib-jdk7",
      kind = null,
      classesRoot = KotlinArtifacts.kotlinStdlibJdk7,
      sourcesRoot = KotlinArtifacts.kotlinStdlibJdk7Sources
    ),
    KOTLIN_STDLIB_JDK8(
      libraryName = "kotlin-stdlib-jdk8",
      kind = null,
      classesRoot = KotlinArtifacts.kotlinStdlibJdk8,
      sourcesRoot = KotlinArtifacts.kotlinStdlibJdk8Sources
    ),
    KOTLIN_STDLIB_JS(
      libraryName = "kotlin-stdlib-js",
      kind = KotlinJavaScriptLibraryKind,
      classesRoot = KotlinArtifacts.kotlinStdlibJs,
      sourcesRoot = KotlinArtifacts.kotlinStdlibSources
    )
}