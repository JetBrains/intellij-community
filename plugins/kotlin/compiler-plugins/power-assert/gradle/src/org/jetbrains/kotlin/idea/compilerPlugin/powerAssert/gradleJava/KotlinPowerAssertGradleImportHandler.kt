// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.powerAssert.gradleJava

import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractGradleImportHandler
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.MavenCoordinates
import java.nio.file.Path

class KotlinPowerAssertGradleImportHandler : AbstractGradleImportHandler() {

    override val pluginJarsToReplaceRegex: List<Regex> = listOf("$POWER_ASSERT_COMPILER_PLUGIN_EMBEDDABLE_JAR_NAME-.*\\.jar".toRegex())

    override val replacementArtifactCoordinates: MavenCoordinates = MavenCoordinates(
        groupId = KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID,
        artifactId = POWER_ASSERT_COMPILER_PLUGIN_ARTIFACT_ID,
    )

    override val replacementJarFromPluginBundle: Path by lazy {
        KotlinArtifacts.powerAssertPluginPath
    }

    companion object {
        private const val POWER_ASSERT_COMPILER_PLUGIN_EMBEDDABLE_JAR_NAME = "kotlin-power-assert-compiler-plugin-embeddable"
        private const val POWER_ASSERT_COMPILER_PLUGIN_ARTIFACT_ID = "kotlin-power-assert-compiler-plugin"
    }
}