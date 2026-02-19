/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.compilerPlugin.assignment.maven

import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.kotlin.assignment.plugin.AssignmentPluginNames.ANNOTATION_OPTION_NAME
import org.jetbrains.kotlin.assignment.plugin.AssignmentPluginNames.PLUGIN_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup.PluginOption
import org.jetbrains.kotlin.idea.maven.compilerPlugin.AbstractMavenImportHandler
import java.nio.file.Path

class AssignmentMavenProjectImportHandler(project: Project) : AbstractMavenImportHandler(project) {
    private companion object {
        const val ANNOTATION_PARAMETER_PREFIX = "assignment:$ANNOTATION_OPTION_NAME="
    }

    override val compilerPluginId: String = PLUGIN_ID
    override val pluginName: String = "assignment"
    override val mavenPluginArtifactName: String = "kotlin-maven-assignment"
    override val pluginJarFileFromIdea: Path = KotlinArtifacts.assignmentCompilerPluginPath

    override fun getOptions(
        mavenProject: MavenProject,
        enabledCompilerPlugins: List<String>,
        compilerPluginOptions: List<String>
    ): List<PluginOption>? {
        if ("assignment" !in enabledCompilerPlugins) {
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
