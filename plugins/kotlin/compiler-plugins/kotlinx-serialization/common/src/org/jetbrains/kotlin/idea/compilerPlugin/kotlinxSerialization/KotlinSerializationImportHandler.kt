// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization

import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifactNames
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import java.io.File

object KotlinSerializationImportHandler {
    val PLUGIN_JPS_JAR: String by lazy { KotlinArtifacts.instance.kotlinxSerializationCompilerPlugin.absolutePath }

    fun isPluginJarPath(path: String): Boolean {
        return path.endsWith(KotlinArtifactNames.KOTLINX_SERIALIZATION_COMPILER_PLUGIN)
    }

    fun modifyCompilerArguments(facet: KotlinFacet, buildSystemPluginJar: String) {
        val facetSettings = facet.configuration.settings
        val commonArguments = facetSettings.compilerArguments ?: CommonCompilerArguments.DummyImpl()

        var pluginWasEnabled = false
        val oldPluginClasspaths = (commonArguments.pluginClasspaths ?: emptyArray()).filterTo(mutableListOf()) {
            val lastIndexOfFile = it.lastIndexOfAny(charArrayOf('/', File.separatorChar))
            if (lastIndexOfFile < 0) {
                return@filterTo true
            }
            val match = it.drop(lastIndexOfFile + 1).matches("$buildSystemPluginJar-.*\\.jar".toRegex())
            if (match) pluginWasEnabled = true
            !match
        }

        val newPluginClasspaths = if (pluginWasEnabled) oldPluginClasspaths + PLUGIN_JPS_JAR else oldPluginClasspaths
        commonArguments.pluginClasspaths = newPluginClasspaths.toTypedArray()
        facetSettings.compilerArguments = commonArguments
    }
}