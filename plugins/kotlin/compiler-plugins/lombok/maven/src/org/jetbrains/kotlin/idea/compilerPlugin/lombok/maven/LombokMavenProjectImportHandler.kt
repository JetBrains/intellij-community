// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.compilerPlugin.lombok.maven

import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.compilerPlugin.CompilerPluginSetup.PluginOption
import org.jetbrains.kotlin.idea.compilerPlugin.toJpsVersionAgnosticKotlinBundledPath
import org.jetbrains.kotlin.idea.maven.compilerPlugin.AbstractMavenImportHandler
import org.jetbrains.kotlin.lombok.LombokPluginNames.CONFIG_OPTION_NAME
import org.jetbrains.kotlin.lombok.LombokPluginNames.PLUGIN_ID
import java.io.File

class LombokMavenProjectImportHandler : AbstractMavenImportHandler() {
    override val compilerPluginId: String = PLUGIN_ID
    override val pluginName: String = MAVEN_SUBPLUGIN_NAME
    override val mavenPluginArtifactName: String = "kotlin-maven-lombok"
    override val pluginJarFileFromIdea: String = KotlinArtifacts.lombokCompilerPlugin.toJpsVersionAgnosticKotlinBundledPath()

    override fun getOptions(
        mavenProject: MavenProject,
        enabledCompilerPlugins: List<String>,
        compilerPluginOptions: List<String>
    ): List<PluginOption>? {
        if (!enabledCompilerPlugins.contains(pluginName)) return null

        return compilerPluginOptions.mapNotNull { option ->
            if (option.startsWith(CONFIG_FILE_PREFIX)) {
                val location = File(option.substring(CONFIG_FILE_PREFIX.length))
                val correctedLocation =
                    if (!location.isAbsolute) File(mavenProject.directory, location.path)
                    else location
                PluginOption(CONFIG_OPTION_NAME, correctedLocation.absolutePath)
            } else {
                null
            }
        }
    }

    companion object {
        private const val MAVEN_SUBPLUGIN_NAME = "lombok"
        private val CONFIG_FILE_PREFIX = "$MAVEN_SUBPLUGIN_NAME:$CONFIG_OPTION_NAME="
    }
}
