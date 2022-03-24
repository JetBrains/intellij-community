// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.allopen.maven

import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.kotlin.allopen.AllOpenCommandLineProcessor
import org.jetbrains.kotlin.idea.maven.compilerPlugin.AbstractMavenImportHandler
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup.PluginOption
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts

class AllOpenMavenProjectImportHandler : AbstractMavenImportHandler() {
    private companion object {
        val ANNOTATION_PARAMETER_PREFIX = "all-open:${AllOpenCommandLineProcessor.ANNOTATION_OPTION.optionName}="
    }

    override val compilerPluginId = AllOpenCommandLineProcessor.PLUGIN_ID
    override val pluginName = "allopen"
    override val mavenPluginArtifactName = "kotlin-maven-allopen"
    override val pluginJarFileFromIdea = KotlinArtifacts.instance.allopenCompilerPlugin

    override fun getOptions(
        mavenProject: MavenProject,
        enabledCompilerPlugins: List<String>,
        compilerPluginOptions: List<String>
    ): List<PluginOption>? {
        if ("all-open" !in enabledCompilerPlugins && "spring" !in enabledCompilerPlugins) {
            return null
        }

        val annotations = mutableListOf<String>()

        for ((presetName, presetAnnotations) in AllOpenCommandLineProcessor.SUPPORTED_PRESETS) {
            if (presetName in enabledCompilerPlugins) {
                annotations.addAll(presetAnnotations)
            }
        }

        annotations.addAll(compilerPluginOptions.mapNotNull { text ->
            if (!text.startsWith(ANNOTATION_PARAMETER_PREFIX)) return@mapNotNull null
            text.substring(ANNOTATION_PARAMETER_PREFIX.length)
        })

        return annotations.map { PluginOption(AllOpenCommandLineProcessor.ANNOTATION_OPTION.optionName, it) }
    }
}
