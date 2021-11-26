// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.samWithReceiver.maven

import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.kotlin.idea.maven.compilerPlugin.AbstractMavenImportHandler
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup.PluginOption
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverCommandLineProcessor

class SamWithReceiverMavenProjectImportHandler : AbstractMavenImportHandler() {
    private companion object {
        val ANNOTATION_PARAMETER_PREFIX = "sam-with-receiver:${SamWithReceiverCommandLineProcessor.ANNOTATION_OPTION.optionName}="
    }

    override val compilerPluginId = SamWithReceiverCommandLineProcessor.PLUGIN_ID
    override val pluginName = "samWithReceiver"
    override val mavenPluginArtifactName = "kotlin-maven-sam-with-receiver"
    override val pluginJarFileFromIdea = KotlinArtifacts.instance.samWithReceiverCompilerPlugin

    override fun getOptions(
        mavenProject: MavenProject,
        enabledCompilerPlugins: List<String>,
        compilerPluginOptions: List<String>
    ): List<PluginOption>? {
        if ("sam-with-receiver" !in enabledCompilerPlugins) {
            return null
        }

        val annotations = mutableListOf<String>()

        for ((presetName, presetAnnotations) in SamWithReceiverCommandLineProcessor.SUPPORTED_PRESETS) {
            if (presetName in enabledCompilerPlugins) {
                annotations.addAll(presetAnnotations)
            }
        }

        annotations.addAll(compilerPluginOptions.mapNotNull { text ->
            if (!text.startsWith(ANNOTATION_PARAMETER_PREFIX)) return@mapNotNull null
            text.substring(ANNOTATION_PARAMETER_PREFIX.length)
        })

        return annotations.map { PluginOption(SamWithReceiverCommandLineProcessor.ANNOTATION_OPTION.optionName, it) }
    }
}
