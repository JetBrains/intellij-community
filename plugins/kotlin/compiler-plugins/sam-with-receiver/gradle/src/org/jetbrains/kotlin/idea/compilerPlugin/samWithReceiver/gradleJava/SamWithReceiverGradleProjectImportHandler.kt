// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.samWithReceiver.gradleJava

import com.intellij.openapi.externalSystem.model.Key
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractAnnotationBasedCompilerPluginGradleImportHandler
import org.jetbrains.kotlin.idea.gradleTooling.model.samWithReceiver.SamWithReceiverModel
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverPluginNames
import java.nio.file.Path

class SamWithReceiverGradleProjectImportHandler : AbstractAnnotationBasedCompilerPluginGradleImportHandler<SamWithReceiverModel>() {
    override val compilerPluginId: String = SamWithReceiverPluginNames.PLUGIN_ID
    override val pluginName: String = "sam-with-receiver"
    override val annotationOptionName: String = SamWithReceiverPluginNames.ANNOTATION_OPTION_NAME
    override val pluginJarFromIdea: Path = KotlinArtifacts.samWithReceiverCompilerPluginPath
    override val modelKey: Key<SamWithReceiverModel> = SamWithReceiverProjectResolverExtension.KEY

    override fun getAnnotationsForPreset(presetName: String): List<String> {
        for ((name, annotations) in SamWithReceiverPluginNames.SUPPORTED_PRESETS.entries) {
            if (presetName == name) {
                return annotations
            }
        }

        return super.getAnnotationsForPreset(presetName)
    }
}
