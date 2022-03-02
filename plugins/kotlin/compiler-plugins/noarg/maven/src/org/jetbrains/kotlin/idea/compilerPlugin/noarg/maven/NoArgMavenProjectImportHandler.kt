// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.noarg.maven

import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.kotlin.idea.maven.compilerPlugin.AbstractMavenImportHandler
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup.PluginOption
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.noarg.NoArgCommandLineProcessor

class NoArgMavenProjectImportHandler : AbstractMavenImportHandler() {
    private companion object {
        val ANNOTATATION_PARAMETER_PREFIX = "no-arg:${NoArgCommandLineProcessor.ANNOTATION_OPTION.optionName}="
        val INVOKEINITIALIZERS_PARAMETER_PREFIX = "no-arg:${NoArgCommandLineProcessor.INVOKE_INITIALIZERS_OPTION.optionName}="
    }

    override val compilerPluginId = NoArgCommandLineProcessor.PLUGIN_ID
    override val pluginName = "noarg"
    override val mavenPluginArtifactName = "kotlin-maven-noarg"
    override val pluginJarFileFromIdea = KotlinArtifacts.instance.noargCompilerPlugin

    override fun getOptions(
        mavenProject: MavenProject,
        enabledCompilerPlugins: List<String>,
        compilerPluginOptions: List<String>
    ): List<PluginOption>? {
        if ("no-arg" !in enabledCompilerPlugins && "jpa" !in enabledCompilerPlugins) {
            return null
        }

        val annotations = mutableListOf<String>()
        for ((presetName, presetAnnotations) in NoArgCommandLineProcessor.SUPPORTED_PRESETS) {
            if (presetName in enabledCompilerPlugins) {
                annotations.addAll(presetAnnotations)
            }
        }

        annotations.addAll(compilerPluginOptions.mapNotNull { text ->
            if (!text.startsWith(ANNOTATATION_PARAMETER_PREFIX)) return@mapNotNull null
            text.substring(ANNOTATATION_PARAMETER_PREFIX.length)
        })

        val options = annotations.mapTo(mutableListOf()) { PluginOption(NoArgCommandLineProcessor.ANNOTATION_OPTION.optionName, it) }

        val invokeInitializerOptionValue = compilerPluginOptions
                .firstOrNull { it.startsWith(INVOKEINITIALIZERS_PARAMETER_PREFIX) }
                ?.drop(INVOKEINITIALIZERS_PARAMETER_PREFIX.length) == "true"

        if (invokeInitializerOptionValue) {
            options.add(PluginOption(NoArgCommandLineProcessor.INVOKE_INITIALIZERS_OPTION.optionName, "true"))
        }

        return options
    }
}
