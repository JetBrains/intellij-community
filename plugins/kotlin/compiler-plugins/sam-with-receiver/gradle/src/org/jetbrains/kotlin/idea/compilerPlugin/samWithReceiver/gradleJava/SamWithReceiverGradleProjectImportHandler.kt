// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.samWithReceiver.gradleJava

import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractAnnotationBasedCompilerPluginGradleImportHandler
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverCommandLineProcessor
import org.jetbrains.kotlin.idea.gradleTooling.model.samWithReceiver.SamWithReceiverModel

class SamWithReceiverGradleProjectImportHandler : AbstractAnnotationBasedCompilerPluginGradleImportHandler<SamWithReceiverModel>() {
    override val compilerPluginId = SamWithReceiverCommandLineProcessor.PLUGIN_ID
    override val pluginName = "sam-with-receiver"
    override val annotationOptionName = SamWithReceiverCommandLineProcessor.ANNOTATION_OPTION.optionName
    override val pluginJarFileFromIdea = KotlinArtifacts.instance.samWithReceiverCompilerPlugin
    override val modelKey = SamWithReceiverProjectResolverExtension.KEY

    override fun getAnnotationsForPreset(presetName: String): List<String> {
        for ((name, annotations) in SamWithReceiverCommandLineProcessor.SUPPORTED_PRESETS.entries) {
            if (presetName == name) {
                return annotations
            }
        }

        return super.getAnnotationsForPreset(presetName)
    }
}
