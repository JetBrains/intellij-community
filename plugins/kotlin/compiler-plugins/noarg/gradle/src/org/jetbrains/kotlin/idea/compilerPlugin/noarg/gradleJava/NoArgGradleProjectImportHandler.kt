// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.noarg.gradleJava

import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractGradleImportHandler
import java.nio.file.Path

class NoArgGradleProjectImportHandler : AbstractGradleImportHandler() {

    override val pluginJarsRegex: List<Regex> = listOf("$NOARG_COMPILER_PLUGIN_JAR_NAME-.*\\.jar".toRegex())
    override val replacedJar: Path = KotlinArtifacts.noargCompilerPluginPath

    companion object {
        private const val NOARG_COMPILER_PLUGIN_JAR_NAME = "kotlin-noarg-compiler-plugin-embeddable"
    }
}
