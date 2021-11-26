// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.compilerPlugin

import com.intellij.openapi.module.Module
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import java.io.File

fun Module.getSpecialAnnotations(prefix: String): List<String> {
    val kotlinFacet = KotlinFacet.get(this) ?: return emptyList()
    val commonArgs = kotlinFacet.configuration.settings.compilerArguments ?: return emptyList()

    return commonArgs.pluginOptions
        ?.filter { it.startsWith(prefix) }
        ?.map { it.substring(prefix.length) }
        ?: emptyList()
}

class CompilerPluginSetup(val options: List<PluginOption>, val classpath: List<String>) {
    class PluginOption(val key: String, val value: String)
}

fun modifyCompilerArgumentsForPlugin(
    facet: KotlinFacet,
    setup: CompilerPluginSetup?,
    compilerPluginId: String,
    pluginName: String
) {
    val facetSettings = facet.configuration.settings

    // investigate why copyBean() sometimes throws exceptions
    val commonArguments = facetSettings.compilerArguments ?: CommonCompilerArguments.DummyImpl()

    /** See [CommonCompilerArguments.PLUGIN_OPTION_FORMAT] **/
    val newOptionsForPlugin = setup?.options?.map { "plugin:$compilerPluginId:${it.key}=${it.value}" } ?: emptyList()

    val oldAllPluginOptions =
        (commonArguments.pluginOptions ?: emptyArray()).filterTo(mutableListOf()) { !it.startsWith("plugin:$compilerPluginId:") }
    val newAllPluginOptions = oldAllPluginOptions + newOptionsForPlugin

    val oldPluginClasspaths = (commonArguments.pluginClasspaths ?: emptyArray()).filterTo(mutableListOf()) {
        val lastIndexOfFile = it.lastIndexOfAny(charArrayOf('/', File.separatorChar))
        if (lastIndexOfFile < 0) {
            return@filterTo true
        }
        !it.drop(lastIndexOfFile + 1).matches("(kotlin-)?(maven-)?$pluginName-.*\\.jar".toRegex())
    }

    val newPluginClasspaths = oldPluginClasspaths + (setup?.classpath ?: emptyList())

    commonArguments.pluginOptions = newAllPluginOptions.toTypedArray()
    commonArguments.pluginClasspaths = newPluginClasspaths.toTypedArray()

    facetSettings.compilerArguments = commonArguments
}