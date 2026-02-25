// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.allopen.gradleJava

import com.intellij.openapi.externalSystem.model.Key
import org.jetbrains.kotlin.allopen.AllOpenPluginNames.ANNOTATION_OPTION_NAME
import org.jetbrains.kotlin.allopen.AllOpenPluginNames.PLUGIN_ID
import org.jetbrains.kotlin.allopen.AllOpenPluginNames.SUPPORTED_PRESETS
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractAnnotationBasedCompilerPluginGradleImportHandler
import org.jetbrains.kotlin.idea.gradleTooling.model.allopen.AllOpenModel
import java.nio.file.Path

class AllOpenGradleProjectImportHandler : AbstractAnnotationBasedCompilerPluginGradleImportHandler<AllOpenModel>() {
    override val compilerPluginId: String = PLUGIN_ID
    override val pluginName: String = "allopen"
    override val annotationOptionName: String = ANNOTATION_OPTION_NAME
    override val pluginJarFromIdea: Path = KotlinArtifacts.allopenCompilerPluginPath
    override val modelKey: Key<AllOpenModel> = AllOpenProjectResolverExtension.KEY

    override fun getAnnotationsForPreset(presetName: String): List<String> {
        for ((name, annotations) in SUPPORTED_PRESETS.entries) {
            if (presetName == name) {
                return annotations
            }
        }

        return super.getAnnotationsForPreset(presetName)
    }
}
