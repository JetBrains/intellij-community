// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.noarg.gradleJava

import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup.PluginOption
import org.jetbrains.kotlin.idea.compilerPlugin.toJpsVersionAgnosticKotlinBundledPath
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractAnnotationBasedCompilerPluginGradleImportHandler
import org.jetbrains.kotlin.idea.gradleTooling.model.noarg.NoArgModel
import org.jetbrains.kotlin.noarg.NoArgPluginNames.ANNOTATION_OPTION_NAME
import org.jetbrains.kotlin.noarg.NoArgPluginNames.INVOKE_INITIALIZERS_OPTION_NAME
import org.jetbrains.kotlin.noarg.NoArgPluginNames.PLUGIN_ID
import org.jetbrains.kotlin.noarg.NoArgPluginNames.SUPPORTED_PRESETS

class NoArgGradleProjectImportHandler : AbstractAnnotationBasedCompilerPluginGradleImportHandler<NoArgModel>() {
    override val compilerPluginId = PLUGIN_ID
    override val pluginName = "noarg"
    override val annotationOptionName = ANNOTATION_OPTION_NAME
    override val pluginJarFileFromIdea = KotlinArtifacts.instance.noargCompilerPlugin.toJpsVersionAgnosticKotlinBundledPath()
    override val modelKey = NoArgProjectResolverExtension.KEY

    override fun getOptions(model: NoArgModel): List<PluginOption> {
        val additionalOptions = listOf(
            PluginOption(
                INVOKE_INITIALIZERS_OPTION_NAME,
                model.invokeInitializers.toString()
            )
        )

        return super.getOptions(model) + additionalOptions
    }

    override fun getAnnotationsForPreset(presetName: String): List<String> {
        for ((name, annotations) in SUPPORTED_PRESETS.entries) {
            if (presetName == name) {
                return annotations
            }
        }

        return super.getAnnotationsForPreset(presetName)
    }
}
