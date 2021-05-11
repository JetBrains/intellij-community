// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.lombok.ide

import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.lombok.LombokCommandLineProcessor
import org.jetbrains.kotlin.lombok.LombokCommandLineProcessor.Companion.CONFIG_FILE_OPTION
import org.jetbrains.kotlin.plugin.ide.AbstractGradleImportHandler
import org.jetbrains.kotlin.plugin.ide.CompilerPluginSetup
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import java.io.File

class LombokGradleProjectImportHandler : AbstractGradleImportHandler<LombokModel>() {

    override val modelKey: Key<LombokModel> = LombokGradleProjectResolverExtension.KEY
    override val pluginJarFileFromIdea: File = KotlinArtifacts.instance.lombokCompilerPlugin
    override val compilerPluginId: String = LombokCommandLineProcessor.PLUGIN_ID
    override val pluginName: String = "lombok"

    override fun getOptions(model: LombokModel): List<CompilerPluginSetup.PluginOption> =
        listOfNotNull(
            model.configurationFile?.let {
                CompilerPluginSetup.PluginOption(CONFIG_FILE_OPTION.optionName, it.path)
            }
        )
}
