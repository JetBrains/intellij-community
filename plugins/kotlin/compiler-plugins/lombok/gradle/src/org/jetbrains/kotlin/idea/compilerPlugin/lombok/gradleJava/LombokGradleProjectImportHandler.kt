// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.compilerPlugin.lombok.gradleJava

import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup
import org.jetbrains.kotlin.idea.compilerPlugin.toJpsVersionAgnosticKotlinBundledPath
import org.jetbrains.kotlin.idea.gradleJava.compilerPlugin.AbstractCompilerPluginGradleImportHandler
import org.jetbrains.kotlin.idea.gradleTooling.model.lombok.LombokModel
import org.jetbrains.kotlin.lombok.LombokPluginNames

class LombokGradleProjectImportHandler : AbstractCompilerPluginGradleImportHandler<LombokModel>() {

    override val modelKey: Key<LombokModel> = LombokGradleProjectResolverExtension.KEY
    override val pluginJarFileFromIdea: String = KotlinArtifacts.instance.lombokCompilerPlugin.toJpsVersionAgnosticKotlinBundledPath()
    override val compilerPluginId: String = LombokPluginNames.PLUGIN_ID
    override val pluginName: String = "lombok"

    override fun getOptions(model: LombokModel): List<CompilerPluginSetup.PluginOption> =
        listOfNotNull(
            model.configurationFile?.let {
                CompilerPluginSetup.PluginOption(LombokPluginNames.CONFIG_OPTION_NAME, it.path)
            }
        )
}
