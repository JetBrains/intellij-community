/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.compilerPlugin.assignment.gradleJava

import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractGradleImportHandler
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.MavenCoordinates
import java.nio.file.Path

class AssignmentGradleProjectImportHandler : AbstractGradleImportHandler() {

    override val pluginJarsToReplaceRegex: List<Regex> = listOf("$ASSIGNMENT_COMPILER_PLUGIN_EMBEDDABLE_JAR_NAME-.*\\.jar".toRegex())

    override val replacementArtifactCoordinates: MavenCoordinates = MavenCoordinates(
        groupId = KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID,
        artifactId = ASSIGNMENT_COMPILER_PLUGIN_ARTIFACT_ID,
    )

    override val replacementJarFromPluginBundle: Path = KotlinArtifacts.assignmentCompilerPluginPath

    companion object {
        private const val ASSIGNMENT_COMPILER_PLUGIN_EMBEDDABLE_JAR_NAME = "kotlin-assignment-compiler-plugin-embeddable"
        private const val ASSIGNMENT_COMPILER_PLUGIN_ARTIFACT_ID = "kotlin-assignment-compiler-plugin"
    }

}
