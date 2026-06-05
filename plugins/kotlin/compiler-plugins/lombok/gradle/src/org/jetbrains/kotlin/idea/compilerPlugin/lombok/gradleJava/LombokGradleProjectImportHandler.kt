// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.compilerPlugin.lombok.gradleJava

import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractGradleImportHandler
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.MavenCoordinates
import java.nio.file.Path

class LombokGradleProjectImportHandler: AbstractGradleImportHandler() {
    override val pluginJarsToReplaceRegex: List<Regex> = listOf("$LOMBOK_COMPILER_PLUGIN_EMBEDDABLE_JAR_NAME-.*\\.jar".toRegex())

    override val replacementArtifactCoordinates: MavenCoordinates = MavenCoordinates(
        groupId = KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID,
        artifactId = LOMBOK_COMPILER_PLUGIN_ARTIFACT_ID,
    )

    override val replacementJarFromPluginBundle: Path = KotlinArtifacts.lombokCompilerPluginPath

    companion object {
        private const val LOMBOK_COMPILER_PLUGIN_EMBEDDABLE_JAR_NAME = "kotlin-lombok-compiler-plugin-embeddable"
        private const val LOMBOK_COMPILER_PLUGIN_ARTIFACT_ID = "kotlin-lombok-compiler-plugin"
    }
}