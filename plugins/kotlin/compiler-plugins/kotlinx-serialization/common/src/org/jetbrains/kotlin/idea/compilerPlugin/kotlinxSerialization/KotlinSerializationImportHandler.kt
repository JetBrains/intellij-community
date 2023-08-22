// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization

import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactNames
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.compilerPlugin.toJpsVersionAgnosticKotlinBundledPath
import java.io.File

object KotlinSerializationImportHandler {
    val PLUGIN_JPS_JAR: String by lazy {
        KotlinArtifacts.kotlinxSerializationCompilerPlugin.toJpsVersionAgnosticKotlinBundledPath()
    }

    fun isPluginJarPath(path: String): Boolean {
        return path.endsWith(KotlinArtifactNames.KOTLINX_SERIALIZATION_COMPILER_PLUGIN)
    }

    fun modifyCompilerArguments(
        facet: KotlinFacet,
        pluginJarsRegex: List<Regex>
    ) {
        val facetSettings = facet.configuration.settings
        val commonArguments = facetSettings.compilerArguments ?: CommonCompilerArguments.DummyImpl()

        var pluginWasEnabled = false
        val oldPluginClasspaths = (commonArguments.pluginClasspaths ?: emptyArray()).filterTo(mutableListOf()) { jarPath ->
            val lastIndexOfFile = jarPath.lastIndexOfAny(charArrayOf('/', File.separatorChar))
            if (lastIndexOfFile < 0) {
                return@filterTo true
            }

            val jarFileName = jarPath.drop(lastIndexOfFile + 1)
            val match = pluginJarsRegex.any { jarFileName.matches(it) }
            if (match) pluginWasEnabled = true
            !match
        }

        val newPluginClasspaths = if (pluginWasEnabled) oldPluginClasspaths + PLUGIN_JPS_JAR else oldPluginClasspaths
        commonArguments.pluginClasspaths = newPluginClasspaths.toTypedArray()
        facetSettings.compilerArguments = commonArguments
    }
}