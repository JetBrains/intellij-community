// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.artifacts

import org.jetbrains.kotlin.utils.KotlinPaths
import java.io.File

/**
 * Analog of [KotlinPaths.ClassPaths] for kotlin-ide
 */
enum class KotlinClassPath(private val jarGetters: List<(KotlinArtifacts) -> File> = emptyList()) {
    Empty(),
    Compiler(
            KotlinArtifacts::kotlinCompiler,
            KotlinArtifacts::kotlinStdlib,
            KotlinArtifacts::kotlinReflect,
            KotlinArtifacts::kotlinScriptRuntime,
            KotlinArtifacts::trove4j,
            KotlinArtifacts::kotlinDaemon
    ),
    CompilerWithScripting(
            Compiler,
            KotlinArtifacts::kotlinScriptingCompiler,
            KotlinArtifacts::kotlinScriptingCompilerImpl,
            KotlinArtifacts::kotlinScriptingCommon,
            KotlinArtifacts::kotlinScriptingJvm,
            KotlinArtifacts::jetbrainsAnnotations
    ),
    MainKts(
            KotlinArtifacts::kotlinMainKts,
            KotlinArtifacts::kotlinScriptRuntime,
            KotlinArtifacts::kotlinStdlib,
            KotlinArtifacts::kotlinReflect
    )
    ;

    fun computeClassPath(): List<File> = this.jarGetters.map { it(KotlinArtifacts.instance) }

    constructor(vararg jarGetters: (KotlinArtifacts) -> File) : this(jarGetters.asList())
    constructor(baseClassPath: KotlinClassPath, vararg jarGetters: (KotlinArtifacts) -> File) : this(baseClassPath.jarGetters + jarGetters)
}
