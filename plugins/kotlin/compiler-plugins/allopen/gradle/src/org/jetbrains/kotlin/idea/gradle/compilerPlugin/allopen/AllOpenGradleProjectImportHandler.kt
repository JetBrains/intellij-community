// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradle.compilerPlugin.allopen

import org.jetbrains.kotlin.allopen.AllOpenCommandLineProcessor
import org.jetbrains.kotlin.allopen.ide.AllOpenModel
import org.jetbrains.kotlin.idea.gradle.compilerPlugin.AbstractGradleImportHandler
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts

class AllOpenGradleProjectImportHandler : AbstractGradleImportHandler<AllOpenModel>() {
    override val compilerPluginId = AllOpenCommandLineProcessor.PLUGIN_ID
    override val pluginName = "allopen"
    override val annotationOptionName = AllOpenCommandLineProcessor.ANNOTATION_OPTION.optionName
    override val pluginJarFileFromIdea = KotlinArtifacts.instance.allopenCompilerPlugin
    override val modelKey = AllOpenProjectResolverExtension.KEY

    override fun getAnnotationsForPreset(presetName: String): List<String> {
        for ((name, annotations) in AllOpenCommandLineProcessor.SUPPORTED_PRESETS.entries) {
            if (presetName == name) {
                return annotations
            }
        }

        return super.getAnnotationsForPreset(presetName)
    }
}
