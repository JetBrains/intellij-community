// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.noarg.gradleJava

import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractGradleImportHandler
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.MavenCoordinates
import java.nio.file.Path

class NoArgGradleProjectImportHandler : AbstractGradleImportHandler() {

    override val pluginJarsToReplaceRegex: List<Regex> = listOf("$NOARG_COMPILER_PLUGIN_EMBEDDABLE_JAR_NAME-.*\\.jar".toRegex())

    override val replacementArtifactCoordinates: MavenCoordinates = MavenCoordinates(
        groupId = KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID,
        artifactId = NOARG_COMPILER_PLUGIN_ARTIFACT_ID,
    )

    override val replacementJarFromPluginBundle: Path = KotlinArtifacts.noargCompilerPluginPath

    companion object {
        private const val NOARG_COMPILER_PLUGIN_EMBEDDABLE_JAR_NAME = "kotlin-noarg-compiler-plugin-embeddable"
        private const val NOARG_COMPILER_PLUGIN_ARTIFACT_ID = "kotlin-noarg-compiler-plugin"
    }
}
