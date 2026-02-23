// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.noarg.maven

import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup.PluginOption
import org.jetbrains.kotlin.idea.maven.compilerPlugin.AbstractMavenImportHandler
import org.jetbrains.kotlin.noarg.NoArgPluginNames.ANNOTATION_OPTION_NAME
import org.jetbrains.kotlin.noarg.NoArgPluginNames.INVOKE_INITIALIZERS_OPTION_NAME
import org.jetbrains.kotlin.noarg.NoArgPluginNames.PLUGIN_ID
import org.jetbrains.kotlin.noarg.NoArgPluginNames.SUPPORTED_PRESETS
import java.nio.file.Path

class NoArgMavenProjectImportHandler : AbstractMavenImportHandler() {
    override val compilerPluginId: String = PLUGIN_ID
    override val pluginName: String = "noarg"
    override val mavenPluginArtifactName: String = "kotlin-maven-noarg"
    override val pluginJarFileFromIdea: Path = KotlinArtifacts.noargCompilerPluginPath

    override fun getOptions(
        mavenProject: MavenProject,
        enabledCompilerPlugins: List<String>,
        compilerPluginOptions: List<String>
    ): List<PluginOption>? {
        if ("no-arg" !in enabledCompilerPlugins && "jpa" !in enabledCompilerPlugins) {
            return null
        }

        val annotations = mutableListOf<String>()
        for ((presetName, presetAnnotations) in SUPPORTED_PRESETS) {
            if (presetName in enabledCompilerPlugins) {
                annotations.addAll(presetAnnotations)
            }
        }

        annotations.addAll(compilerPluginOptions.mapNotNull { text ->
            if (!text.startsWith(ANNOTATION_PARAMETER_PREFIX)) return@mapNotNull null
            text.substring(ANNOTATION_PARAMETER_PREFIX.length)
        })

        val options = annotations.mapTo(mutableListOf()) { PluginOption(ANNOTATION_OPTION_NAME, it) }

        val invokeInitializerOptionValue = compilerPluginOptions
                .firstOrNull { it.startsWith(INVOKEINITIALIZERS_PARAMETER_PREFIX) }
                ?.drop(INVOKEINITIALIZERS_PARAMETER_PREFIX.length) == "true"

        if (invokeInitializerOptionValue) {
            options.add(PluginOption(INVOKE_INITIALIZERS_OPTION_NAME, "true"))
        }

        return options
    }
}

private const val ANNOTATION_PARAMETER_PREFIX = "no-arg:$ANNOTATION_OPTION_NAME="
private const val INVOKEINITIALIZERS_PARAMETER_PREFIX = "no-arg:$INVOKE_INITIALIZERS_OPTION_NAME="