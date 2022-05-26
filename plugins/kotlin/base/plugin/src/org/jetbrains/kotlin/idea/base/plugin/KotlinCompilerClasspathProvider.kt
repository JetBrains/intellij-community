// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.plugin

import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import java.io.File

object KotlinCompilerClasspathProvider {
    class Classpath(
        private val parent: Classpath?,
        vararg jarFactories: (KotlinArtifacts) -> File
    ): Lazy<List<File>> by lazy({ parent?.value.orEmpty() + jarFactories.map { it(KotlinArtifacts.instance) } })

    private val compilerClasspath = Classpath(
        null,
        KotlinArtifacts::kotlinCompiler,
        KotlinArtifacts::kotlinStdlib,
        KotlinArtifacts::kotlinReflect,
        KotlinArtifacts::kotlinScriptRuntime,
        KotlinArtifacts::trove4j,
        KotlinArtifacts::kotlinDaemon
    )

    val compilerWithScriptingClasspath = Classpath(
        compilerClasspath,
        KotlinArtifacts::kotlinScriptingCompiler,
        KotlinArtifacts::kotlinScriptingCompilerImpl,
        KotlinArtifacts::kotlinScriptingCommon,
        KotlinArtifacts::kotlinScriptingJvm,
        KotlinArtifacts::jetbrainsAnnotations
    )
}