// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.samWithReceiver.gradleJava

import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compilerPlugin.toJpsVersionAgnosticKotlinBundledPath
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractAnnotationBasedCompilerPluginGradleImportHandler
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverPluginNames
import org.jetbrains.kotlin.idea.gradleTooling.model.samWithReceiver.SamWithReceiverModel

class SamWithReceiverGradleProjectImportHandler : AbstractAnnotationBasedCompilerPluginGradleImportHandler<SamWithReceiverModel>() {
    override val compilerPluginId = SamWithReceiverPluginNames.PLUGIN_ID
    override val pluginName = "sam-with-receiver"
    override val annotationOptionName = SamWithReceiverPluginNames.ANNOTATION_OPTION_NAME
    override val pluginJarFileFromIdea = KotlinArtifacts.instance.samWithReceiverCompilerPlugin.toJpsVersionAgnosticKotlinBundledPath()
    override val modelKey = SamWithReceiverProjectResolverExtension.KEY

    override fun getAnnotationsForPreset(presetName: String): List<String> {
        for ((name, annotations) in SamWithReceiverPluginNames.SUPPORTED_PRESETS.entries) {
            if (presetName == name) {
                return annotations
            }
        }

        return super.getAnnotationsForPreset(presetName)
    }
}
