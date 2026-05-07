// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.gradleJava

import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractGradleImportHandler
import java.nio.file.Path

class KotlinSerializationGradleImportHandler : AbstractGradleImportHandler() {

    override val pluginJarsRegex: List<Regex> = listOf(
        "$PLUGIN_COMPILER_EMBEDDABLE_JAR_NAME-.*\\.jar".toRegex(),
        "$PLUGIN_COMPILER_JAR_NAME-.*\\.jar".toRegex(),
        "$PLUGIN_GRADLE_JAR_NAME-.*\\.jar".toRegex()
    )
    override val replacedJar: Path by lazy {
        KotlinArtifacts.kotlinxSerializationCompilerPluginPath
    }

    companion object {
        private const val PLUGIN_GRADLE_JAR_NAME = "kotlin-serialization"
        private const val PLUGIN_COMPILER_EMBEDDABLE_JAR_NAME = "kotlinx-serialization-compiler-plugin-embeddable"
        private const val PLUGIN_COMPILER_JAR_NAME = "kotlinx-serialization-compiler-plugin"
    }
}