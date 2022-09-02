// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.valueContainerAssignment.maven

import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.kotlin.container.assignment.ValueContainerAssignmentPluginNames.ANNOTATION_OPTION_NAME
import org.jetbrains.kotlin.container.assignment.ValueContainerAssignmentPluginNames.PLUGIN_ID
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup.PluginOption
import org.jetbrains.kotlin.idea.compilerPlugin.toJpsVersionAgnosticKotlinBundledPath
import org.jetbrains.kotlin.idea.maven.compilerPlugin.AbstractMavenImportHandler

class ValueContainerAssignmentMavenProjectImportHandler : AbstractMavenImportHandler() {
    private companion object {
        const val ANNOTATION_PARAMETER_PREFIX = "value-container-assignment:$ANNOTATION_OPTION_NAME="
    }

    override val compilerPluginId = PLUGIN_ID
    override val pluginName = "valueContainerAssignment"
    override val mavenPluginArtifactName = "kotlin-maven-value-container-assignment"
    override val pluginJarFileFromIdea = KotlinArtifacts.instance.valueContainerAssignmentCompilerPlugin.toJpsVersionAgnosticKotlinBundledPath()

    override fun getOptions(
        mavenProject: MavenProject,
        enabledCompilerPlugins: List<String>,
        compilerPluginOptions: List<String>
    ): List<PluginOption>? {
        if ("value-container-assignment" !in enabledCompilerPlugins) {
            return null
        }

        val annotations = mutableListOf<String>()

        annotations.addAll(compilerPluginOptions.mapNotNull { text ->
            if (!text.startsWith(ANNOTATION_PARAMETER_PREFIX)) return@mapNotNull null
            text.substring(ANNOTATION_PARAMETER_PREFIX.length)
        })

        return annotations.map { PluginOption(ANNOTATION_OPTION_NAME, it) }
    }
}
