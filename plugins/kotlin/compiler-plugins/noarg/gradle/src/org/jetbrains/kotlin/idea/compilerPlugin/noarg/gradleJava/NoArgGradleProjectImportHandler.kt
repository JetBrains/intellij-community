// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.noarg.gradleJava

import com.intellij.openapi.externalSystem.model.Key
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup.PluginOption
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractAnnotationBasedCompilerPluginGradleImportHandler
import org.jetbrains.kotlin.idea.gradleTooling.model.noarg.NoArgModel
import org.jetbrains.kotlin.noarg.NoArgPluginNames.ANNOTATION_OPTION_NAME
import org.jetbrains.kotlin.noarg.NoArgPluginNames.INVOKE_INITIALIZERS_OPTION_NAME
import org.jetbrains.kotlin.noarg.NoArgPluginNames.PLUGIN_ID
import org.jetbrains.kotlin.noarg.NoArgPluginNames.SUPPORTED_PRESETS
import java.nio.file.Path

class NoArgGradleProjectImportHandler : AbstractAnnotationBasedCompilerPluginGradleImportHandler<NoArgModel>() {
    override val compilerPluginId: String = PLUGIN_ID
    override val pluginName: String = "noarg"
    override val annotationOptionName: String = ANNOTATION_OPTION_NAME
    override val pluginJarFromIdea: Path = KotlinArtifacts.noargCompilerPluginPath
    override val modelKey: Key<NoArgModel> = NoArgProjectResolverExtension.KEY

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
