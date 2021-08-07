// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradle.compilerPlugin.noarg

import org.jetbrains.kotlin.idea.gradle.compilerPlugin.AbstractGradleImportHandler
import org.jetbrains.kotlin.idea.compilerPlugin.AnnotationBasedCompilerPluginSetup.PluginOption
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.noarg.NoArgCommandLineProcessor
import org.jetbrains.kotlin.noarg.ide.NoArgModel

class NoArgGradleProjectImportHandler : AbstractGradleImportHandler<NoArgModel>() {
    override val compilerPluginId = NoArgCommandLineProcessor.PLUGIN_ID
    override val pluginName = "noarg"
    override val annotationOptionName = NoArgCommandLineProcessor.ANNOTATION_OPTION.optionName
    override val pluginJarFileFromIdea = KotlinArtifacts.instance.noargCompilerPlugin
    override val modelKey = NoArgProjectResolverExtension.KEY

    override fun getAdditionalOptions(model: NoArgModel): List<PluginOption> {
        return listOf(PluginOption(
                NoArgCommandLineProcessor.INVOKE_INITIALIZERS_OPTION.optionName,
                model.invokeInitializers.toString()))
    }

    override fun getAnnotationsForPreset(presetName: String): List<String> {
        for ((name, annotations) in NoArgCommandLineProcessor.SUPPORTED_PRESETS.entries) {
            if (presetName == name) {
                return annotations
            }
        }

        return super.getAnnotationsForPreset(presetName)
    }
}
