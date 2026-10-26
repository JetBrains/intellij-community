// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.scratch

import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactNames

val kotlincIdeScratchClasspathArtifactFileNames: List<String> = listOf(
    KotlinArtifactNames.KOTLIN_COMPILER,
    KotlinArtifactNames.KOTLIN_STDLIB,
    KotlinArtifactNames.KOTLIN_REFLECT,
    KotlinArtifactNames.KOTLIN_SCRIPT_RUNTIME,
    KotlinArtifactNames.KOTLINX_COROUTINES_CORE_JVM,
    KotlinArtifactNames.KOTLIN_DAEMON,
    KotlinArtifactNames.POWER_ASSERT_COMPILER_PLUGIN,
    KotlinArtifactNames.KOTLIN_SCRIPTING_COMPILER,
    KotlinArtifactNames.KOTLIN_SCRIPTING_COMPILER_IMPL,
    KotlinArtifactNames.KOTLIN_SCRIPTING_COMMON,
    KotlinArtifactNames.KOTLIN_SCRIPTING_JVM,
    KotlinArtifactNames.JETBRAINS_ANNOTATIONS,
)