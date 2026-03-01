// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization

import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactNames
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import java.io.File
import java.nio.file.Path

object KotlinSerializationImportHandler {
    val PLUGIN_JPS_JAR: Path by lazy {
        KotlinArtifacts.kotlinxSerializationCompilerPluginPath
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
        val oldPluginClasspaths = (commonArguments.pluginClasspaths?.map { Path.of(it) }?.toTypedArray() ?: emptyArray()).filterTo(mutableListOf()) { jarPath ->
            val str = jarPath.toString()
            val lastIndexOfFile = str.lastIndexOfAny(charArrayOf('/', File.separatorChar))
            if (lastIndexOfFile < 0) {
                return@filterTo true
            }

            val jarFileName = str.drop(lastIndexOfFile + 1)
            val match = pluginJarsRegex.any { jarFileName.matches(it) }
            if (match) pluginWasEnabled = true
            !match
        }

        val newPluginClasspaths = if (pluginWasEnabled) {
            buildList {
                addAll(oldPluginClasspaths)
                add(PLUGIN_JPS_JAR)
            }
        } else {
            oldPluginClasspaths
        }
        commonArguments.pluginClasspaths = newPluginClasspaths.map { it.toString() }.toTypedArray()
        facetSettings.compilerArguments = commonArguments
    }
}